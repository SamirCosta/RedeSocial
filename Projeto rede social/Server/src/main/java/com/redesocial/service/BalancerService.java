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
        this.syncPort = port + 1030; // Porta base para sincronização
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
            // Socket para receber requisições dos clientes
            ZMQ.Socket frontendSocket = context.createSocket(SocketType.ROUTER);
            frontendSocket.bind(bindAddress);

            // Socket para enviar requisições para os servidores
            ZMQ.Socket backendSocket = context.createSocket(SocketType.DEALER);

            logger.log("Serviço de balanceamento iniciado em: " + bindAddress);

            while (running.get()) {
                // Aguarda uma requisição
                byte[] identity = frontendSocket.recv();
                if (identity == null) continue;

                byte[] empty = frontendSocket.recv(); // Recebe o frame vazio
                byte[] requestBytes = frontendSocket.recv();

                if (requestBytes == null) continue;

                String requestStr = new String(requestBytes, StandardCharsets.UTF_8);
                logger.log("Requisição recebida no balanceador: " + requestStr);

                // Seleciona um servidor usando Round Robin
                LoadBalancer.ServerInfo server = loadBalancer.getNextServer();
                if (server == null) {
                    // Responde ao cliente que não há servidores disponíveis
                    String errorResponse = createErrorResponse("Nenhum servidor disponível");
                    frontendSocket.send(identity, ZMQ.SNDMORE);
                    frontendSocket.send(empty, ZMQ.SNDMORE);
                    frontendSocket.send(errorResponse.getBytes(StandardCharsets.UTF_8));
                    continue;
                }

                // Determina qual porta usar com base na ação da requisição
                int portOffset = getPortOffsetForRequest(requestStr);

                // CORREÇÃO: Usa a porta do serviço (server.port) em vez da porta de sincronização
                // Conecta-se diretamente à porta do serviço + offset apropriado
                String serverAddress = "tcp://" + server.getAddress() + ":" + (server.getPort() + portOffset);
                try (ZContext serverContext = new ZContext()) {
                    ZMQ.Socket serverSocket = serverContext.createSocket(SocketType.REQ);
                    serverSocket.connect(serverAddress);

                    // Envia a requisição para o servidor
                    serverSocket.send(requestBytes);
                    logger.log("Requisição encaminhada para o servidor: " + serverAddress);

                    // Recebe a resposta do servidor
                    byte[] responseBytes = serverSocket.recv();
                    if (responseBytes == null) {
                        String errorResponse = createErrorResponse("Erro na comunicação com o servidor");
                        frontendSocket.send(identity, ZMQ.SNDMORE);
                        frontendSocket.send(empty, ZMQ.SNDMORE);
                        frontendSocket.send(errorResponse.getBytes(StandardCharsets.UTF_8));
                        continue;
                    }

                    // Envia a resposta de volta para o cliente
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

    /**
     * Executa o serviço de sincronização para comunicação entre servidores
     */
    private void runSyncService() {
        try (ZContext context = new ZContext()) {
            // Socket para comunicação de sincronização
            ZMQ.Socket syncSocket = context.createSocket(SocketType.REP);

            // Tentativa de bind com várias portas para evitar conflito
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
                // Aguarda uma mensagem de sincronização
                byte[] messageBytes = syncSocket.recv();
                if (messageBytes == null) continue;

                String messageStr = new String(messageBytes, StandardCharsets.UTF_8);
                logger.log("Mensagem de sincronização recebida: " + messageStr);

                // Processa a mensagem de sincronização
                String response = processSyncMessage(messageStr);

                // Envia a resposta
                syncSocket.send(response.getBytes(StandardCharsets.UTF_8));
                logger.log("Resposta de sincronização enviada");
            }
        } catch (Exception e) {
            logger.logError("Erro no serviço de sincronização do balanceador", e);
        }
    }

    /**
     * Processa uma mensagem de sincronização
     *
     * @param messageStr String com a mensagem JSON
     * @return String com a resposta
     */
    private String processSyncMessage(String messageStr) {
        try {
            JSONObject message = new JSONObject(messageStr);
            String action = message.getString("action");

            // Cria resposta padrão
            JSONObject response = new JSONObject();
            response.put("success", true);

            // Adiciona logicalTime se estiver presente na mensagem original
            if (message.has("logicalTime")) {
                long logicalTime = message.getLong("logicalTime");
                // Incrementa o tempo lógico para a resposta
                response.put("logicalTime", logicalTime + 1);
            }

            // Processa a ação
            switch (action) {
                case "IS_COORDINATOR_REQUEST":
                    // O balanceador não é coordenador, mas responde para não quebrar o protocolo
                    response.put("isCoordinator", false);
                    break;

                case "SERVER_PING":
                    // Ping para verificar se o balanceador está ativo
                    String fromServerId = message.getString("fromServer");
                    response.put("serverId", "balancer");
                    response.put("isActive", true);
                    logger.log("Ping recebido do servidor: " + fromServerId);
                    break;

                case "SERVER_ANNOUNCEMENT":
                    // Anúncio de servidor na rede
                    String serverId = message.getString("serverId");
                    String serverAddress = message.getString("serverAddress");
                    int serverPort = message.getInt("serverPort");

                    // CORREÇÃO: Armazenar a porta de serviço correta em vez da porta de sincronização
                    // Verificando se a informação do servidor já inclui a porta de serviço
                    if (message.has("servicePort")) {
                        serverPort = message.getInt("servicePort");
                    } else {
                        // Se não tiver, usamos uma lógica para identificar a porta de serviço
                        // Assumimos que as portas de serviço são 5555, 5556, 5557, etc.
                        if (serverId.equals("server1")) {
                            serverPort = 5555;
                        } else if (serverId.equals("server2")) {
                            serverPort = 5556;
                        } else if (serverId.equals("server3")) {
                            serverPort = 5557;
                        }
                    }

                    // Adiciona ou atualiza o servidor no balanceador
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
                    // O balanceador não participa dessas ações, mas responde para não quebrar o protocolo
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

    /**
     * Determina o offset de porta com base na ação da requisição
     *
     * @param requestStr A requisição em formato de string
     * @return O offset de porta adequado
     */
    private int getPortOffsetForRequest(String requestStr) {
        try {
            JSONObject request = new JSONObject(requestStr);
            String action = request.getString("action");

            // Determina o serviço com base na ação
            if (action.startsWith("USER_") || action.equals("register")) {
                // Direciona para o UserService (porta base + 300)
                return 300;
            } else if (action.equals("FOLLOW_USER") || action.equals("UNFOLLOW_USER") ||
                    action.equals("GET_FOLLOWERS") || action.equals("GET_FOLLOWING")) {
                // Direciona para o FollowService (porta base + 200)
                return 200;
            } else if (action.equals("SEND_MESSAGE") || action.equals("MARK_AS_READ") ||
                    action.equals("GET_CONVERSATION") || action.equals("GET_UNREAD_MESSAGES")) {
                // Direciona para o MessageService (porta base + 100)
                return 100;
            } else {
                // Direciona para o PostService (porta base + 0)
                return 0;
            }
        } catch (Exception e) {
            logger.logError("Erro ao analisar a requisição", e);
            // Por padrão, direciona para o PostService
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