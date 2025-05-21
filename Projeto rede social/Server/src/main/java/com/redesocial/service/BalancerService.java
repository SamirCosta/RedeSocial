package com.redesocial.service;

import com.redesocial.server.LoadBalancer;
import com.redesocial.util.EventLogger;

import org.json.JSONObject;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BalancerService {
    private final LoadBalancer loadBalancer;
    private final EventLogger logger;
    private final String bindAddress;
    private final String syncAddress;
    private final int syncPort;
    private final ExecutorService executor;
    private final ExecutorService syncExecutor;
    private final AtomicBoolean running;

    public BalancerService(LoadBalancer loadBalancer, EventLogger logger, String address, int port) {
        this.loadBalancer = loadBalancer;
        this.logger = logger;
        this.bindAddress = "tcp://" + address + ":" + port;
        this.syncAddress = address;
        this.syncPort = port + 1030;
        this.executor = Executors.newSingleThreadExecutor();
        this.syncExecutor = Executors.newSingleThreadExecutor();
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.submit(this::runService);
            syncExecutor.submit(this::runSyncService);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            executor.shutdown();
            syncExecutor.shutdown();
            logger.log("Serviço de balanceamento parado");
        }
    }

    private void runService() {
        try (ZContext context = new ZContext()) {

            ZMQ.Socket frontendSocket = context.createSocket(SocketType.ROUTER);
            frontendSocket.bind(bindAddress);

            ZMQ.Socket backendSocket = context.createSocket(SocketType.DEALER);

            logger.log("Serviço de balanceamento iniciado em: " + bindAddress);

            while (running.get()) {

                byte[] identity = frontendSocket.recv();
                if (identity == null) continue;

                byte[] empty = frontendSocket.recv();
                byte[] requestBytes = frontendSocket.recv();

                if (requestBytes == null) continue;

                String requestStr = new String(requestBytes, StandardCharsets.UTF_8);
                logger.log("Requisição recebida no balanceador: " + requestStr);

                LoadBalancer.ServerInfo server = loadBalancer.getNextServer();
                if (server == null) {
                    String errorResponse = createErrorResponse("Nenhum servidor disponível");
                    frontendSocket.send(identity, ZMQ.SNDMORE);
                    frontendSocket.send(empty, ZMQ.SNDMORE);
                    frontendSocket.send(errorResponse.getBytes(StandardCharsets.UTF_8));
                    continue;
                }

                int portOffset = getPortOffsetForRequest(requestStr);

                String serverAddress = "tcp://" + server.getAddress() + ":" + (server.getPort() + portOffset);
                try (ZContext serverContext = new ZContext()) {
                    ZMQ.Socket serverSocket = serverContext.createSocket(SocketType.REQ);
                    serverSocket.connect(serverAddress);

                    serverSocket.send(requestBytes);
                    logger.log("Requisição encaminhada para o servidor: " + serverAddress);

                    byte[] responseBytes = serverSocket.recv();
                    if (responseBytes == null) {
                        String errorResponse = createErrorResponse("Erro na comunicação com o servidor");
                        frontendSocket.send(identity, ZMQ.SNDMORE);
                        frontendSocket.send(empty, ZMQ.SNDMORE);
                        frontendSocket.send(errorResponse.getBytes(StandardCharsets.UTF_8));
                        continue;
                    }

                    frontendSocket.send(identity, ZMQ.SNDMORE);
                    frontendSocket.send(empty, ZMQ.SNDMORE);
                    frontendSocket.send(responseBytes);

                    logger.log("Resposta enviada ao cliente");
                }
            }
        } catch (Exception e) {
            logger.logError("Erro no serviço de balanceamento", e);
        }
    }

    private void runSyncService() {
        try (ZContext context = new ZContext()) {

            ZMQ.Socket syncSocket = context.createSocket(SocketType.REP);

            boolean bindSuccess = false;
            int maxAttempts = 5;
            int portOffset = 0;

            while (!bindSuccess && portOffset < maxAttempts) {
                try {
                    int currentPort = syncPort + portOffset;
                    String currentBindAddress = "tcp://" + syncAddress + ":" + currentPort;
                    logger.log("Tentando iniciar serviço de sincronização do balanceador em: " + currentBindAddress + " (tentativa " + (portOffset + 1) + ")");
                    syncSocket.bind(currentBindAddress);
                    bindSuccess = true;
                    logger.log("Serviço de sincronização do balanceador iniciado em: " + currentBindAddress);
                } catch (Exception e) {
                    logger.log("Falha ao iniciar serviço de sincronização na porta. Tentando próxima porta: " + e.getMessage());
                    portOffset++;
                }
            }

            if (!bindSuccess) {
                logger.log("Não foi possível iniciar o serviço de sincronização após " + maxAttempts + " tentativas");
                return;
            }

            while (running.get()) {
                byte[] messageBytes = syncSocket.recv();
                if (messageBytes == null) continue;

                String messageStr = new String(messageBytes, StandardCharsets.UTF_8);
                logger.log("Mensagem de sincronização recebida: " + messageStr);

                String response = processSyncMessage(messageStr);

                syncSocket.send(response.getBytes(StandardCharsets.UTF_8));
                logger.log("Resposta de sincronização enviada");
            }
        } catch (Exception e) {
            logger.logError("Erro no serviço de sincronização do balanceador", e);
        }
    }

    private String processSyncMessage(String messageStr) {
        try {
            JSONObject message = new JSONObject(messageStr);
            String action = message.getString("action");

            JSONObject response = new JSONObject();
            response.put("success", true);

            if (message.has("logicalTime")) {
                long logicalTime = message.getLong("logicalTime");

                response.put("logicalTime", logicalTime + 1);
            }

            switch (action) {
                case "IS_COORDINATOR_REQUEST":
                    response.put("isCoordinator", false);
                    break;

                case "SERVER_PING":
                    String fromServerId = message.getString("fromServer");
                    response.put("serverId", "balancer");
                    response.put("isActive", true);
                    logger.log("Ping recebido do servidor: " + fromServerId);
                    break;

                case "SERVER_ANNOUNCEMENT":
                    String serverId = message.getString("serverId");
                    String serverAddress = message.getString("serverAddress");
                    int serverPort = message.getInt("serverPort");

                    if (message.has("servicePort")) {
                        serverPort = message.getInt("servicePort");
                    } else {
                        if (serverId.equals("server1")) {
                            serverPort = 5555;
                        } else if (serverId.equals("server2")) {
                            serverPort = 5556;
                        } else if (serverId.equals("server3")) {
                            serverPort = 5557;
                        }
                    }

                    loadBalancer.addServer(serverId, serverAddress, serverPort);
                    logger.log("Servidor registrado no balanceador: " + serverId + " em " + serverAddress + ":" + serverPort);
                    break;

                case "COORDINATOR_PING":
                case "COORDINATOR_HEARTBEAT":
                case "TIME_REQUEST":
                case "TIME_RESPONSE":
                case "CLOCK_ADJUSTMENT":
                case "ELECTION":
                case "ELECTION_RESPONSE":
                case "COORDINATOR":
                    logger.log("Ação de sincronização não processada pelo balanceador: " + action);
                    break;

                default:
                    response.put("success", false);
                    response.put("error", "Ação desconhecida: " + action);
            }

            return response.toString();
        } catch (Exception e) {
            logger.logError("Erro ao processar mensagem de sincronização", e);

            JSONObject errorResponse = new JSONObject();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return errorResponse.toString();
        }
    }

    private int getPortOffsetForRequest(String requestStr) {
        try {
            JSONObject request = new JSONObject(requestStr);
            String action = request.getString("action");

            if (action.startsWith("USER_") || action.equals("register")) {
                return 300;
            } else if (action.equals("FOLLOW_USER") || action.equals("UNFOLLOW_USER") ||
                    action.equals("GET_FOLLOWERS") || action.equals("GET_FOLLOWING")) {
                return 200;
            } else if (action.equals("SEND_MESSAGE") || action.equals("MARK_AS_READ") ||
                    action.equals("GET_CONVERSATION") || action.equals("GET_UNREAD_MESSAGES")) {
                return 100;
            } else {
                return 0;
            }
        } catch (Exception e) {
            logger.logError("Erro ao analisar a requisição", e);
            return 0;
        }
    }

    private String createErrorResponse(String message) {
        JSONObject response = new JSONObject();
        response.put("success", false);
        response.put("error", message);
        return response.toString();
    }
}