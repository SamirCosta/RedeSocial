package com.redesocial.clock;

import com.redesocial.model.ServerState;
import com.redesocial.service.DataReplicationService;
import com.redesocial.service.ServerDiscoveryService;
import com.redesocial.util.EventLogger;
import org.json.JSONObject;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerCommunication {
    private final ServerState serverState;
    private final EventLogger logger;
    private final String syncBindAddress;
    private final int syncPort;
    private final ZContext context;
    private final ExecutorService receiverExecutor;
    private final ExecutorService senderExecutor;
    private final AtomicBoolean running;
    private BerkeleyAlgorithm berkeleyAlgorithm;
    private BullyElection bullyElection;
    private ServerDiscoveryService discoveryService;
    private DataReplicationService replicationService;
    private final Map<String, Long> lastConnectionAttempt = new ConcurrentHashMap<>();
    private static final long CONNECTION_RETRY_INTERVAL_MS = 10000; // 10 segundos

    public ServerCommunication(ServerState serverState, EventLogger logger, int syncPort) {
        this.serverState = serverState;
        this.logger = logger;
        this.syncPort = syncPort;
        this.syncBindAddress = "tcp://" + serverState.getServerAddress() + ":" + syncPort;
        this.context = new ZContext();
        this.receiverExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Receiver-Thread");
            t.setDaemon(true);
            return t;
        });
        this.senderExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Sender-Thread");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);

        logger.log("Inicializando comunicação no endereço: " + this.syncBindAddress);
    }

    public void setReplicationService(DataReplicationService replicationService) {
        this.replicationService = replicationService;
    }

    public void initializeSynchronization(long syncIntervalMs, long coordinatorCheckIntervalMs) {

        this.berkeleyAlgorithm = new BerkeleyAlgorithm(
                serverState, logger, this, syncIntervalMs
        );

        this.bullyElection = new BullyElection(
                serverState, logger, this, coordinatorCheckIntervalMs
        );

        this.discoveryService = new ServerDiscoveryService(
                serverState, logger, this, 15000
        );
    }

    public void start() {
        if (running.compareAndSet(false, true)) {

            receiverExecutor.submit(this::receiveMessages);

            try {
                if (TimeManager.getInstance() == null) {
                    TimeManager.initialize(serverState, logger);
                }
            } catch (Exception e) {
                TimeManager.initialize(serverState, logger);
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            senderExecutor.submit(() -> {
                try {
                    Thread.sleep(2000);
                    bullyElection.startCoordinatorCheck();

                    Thread.sleep(1000);
                    berkeleyAlgorithm.startSynchronization();

                    Thread.sleep(1000);
                    discoveryService.start();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            logger.log("Comunicação entre servidores iniciada em " + syncBindAddress);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.log("Parando comunicação entre servidores...");

            try {
                berkeleyAlgorithm.stopSynchronization();
            } catch (Exception e) {
                logger.logError("Erro ao parar algoritmo de Berkeley", e);
            }

            try {
                bullyElection.stopCoordinatorCheck();
            } catch (Exception e) {
                logger.logError("Erro ao parar algoritmo de eleição", e);
            }

            try {
                discoveryService.stop();
            } catch (Exception e) {
                logger.logError("Erro ao parar serviço de descoberta", e);
            }

            shutdownExecutorService(receiverExecutor, "Receptor");
            shutdownExecutorService(senderExecutor, "Sender");

            try {
                context.close();
            } catch (Exception e) {
                logger.logError("Erro ao fechar contexto ZeroMQ", e);
            }

            logger.log("Comunicação entre servidores parada");
        }
    }

    private void shutdownExecutorService(ExecutorService executor, String name) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.logError("ExecutorService " + name + " não terminou", null);
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void receiveMessages() {
        ZMQ.Socket socket = null;
        boolean bindSuccessful = false;

        try {
            socket = context.createSocket(SocketType.REP);

            socket.setSendTimeOut(3000);
            socket.setReceiveTimeOut(1000);

            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    int portOffset = attempt;
                    String bindAddress = "tcp://" + serverState.getServerAddress() + ":" + (syncPort + portOffset);
                    logger.log("Tentando bind em " + bindAddress + " (tentativa " + (attempt + 1) + " de 5)");
                    socket.bind(bindAddress);
                    logger.log("Bind bem-sucedido em " + bindAddress);
                    bindSuccessful = true;
                    break;
                } catch (ZMQException e) {
                    logger.logError("Tentativa " + (attempt + 1) + " falhou: " + e.getMessage(), e);
                    if (attempt < 4) {
                        try {
                            Thread.sleep(1000 * (attempt + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }

            if (!bindSuccessful) {
                logger.logError("Não foi possível fazer bind após 5 tentativas", null);
                return;
            }

            logger.log("Receptor de mensagens de sincronização iniciado com sucesso");

            while (running.get()) {
                try {
                    byte[] messageBytes = socket.recv();
                    if (messageBytes == null) {
                        continue;
                    }

                    String messageStr = new String(messageBytes, StandardCharsets.UTF_8);
                    logger.log("Mensagem recebida: " + messageStr);

                    String response = processMessage(messageStr);
                    socket.send(response.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    if (running.get()) {
                        logger.logError("Erro ao processar mensagem recebida", e);
                    }
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                logger.logError("Erro no receptor de mensagens", e);
            }
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    logger.logError("Erro ao fechar socket receptor", e);
                }
            }
        }
    }

    private String processMessage(String messageStr) {
        try {
            JSONObject message = new JSONObject(messageStr);
            String action = message.getString("action");

            if (message.has("logicalTime")) {
                long receivedLogicalTime = message.getLong("logicalTime");
                TimeManager.getInstance().updateLogicalClock(receivedLogicalTime);
            }

            JSONObject response = new JSONObject();
            response.put("success", true);

            switch (action) {
                case "TIME_REQUEST":
                    String coordinatorId = message.getString("coordinator");
                    long coordinatorTimestamp = message.getLong("timestamp");
                    berkeleyAlgorithm.processTimeRequest(coordinatorId, coordinatorTimestamp);
                    break;

                case "TIME_RESPONSE":
                    String respondingServerId = message.getString("serverId");
                    long timeDifference = message.getLong("timeDifference");
                    berkeleyAlgorithm.processTimeResponse(respondingServerId, timeDifference);
                    break;

                case "CLOCK_ADJUSTMENT":
                    String adjCoordinatorId = message.getString("coordinator");
                    long adjustment = message.getLong("adjustment");
                    berkeleyAlgorithm.applyClockAdjustment(adjCoordinatorId, adjustment);
                    break;

                case "ELECTION":
                    String electionStarterId = message.getString("fromServer");
                    bullyElection.processElectionMessage(electionStarterId);
                    break;

                case "ELECTION_RESPONSE":
                    String electionResponderId = message.getString("fromServer");
                    bullyElection.processElectionResponse(electionResponderId);
                    break;

                case "COORDINATOR":
                    String newCoordinatorId = message.getString("coordinatorId");
                    bullyElection.processCoordinatorMessage(newCoordinatorId);
                    break;

                case "COORDINATOR_HEARTBEAT":
                    break;

                case "COORDINATOR_PING":
                    break;

                case "SERVER_ANNOUNCEMENT":
                    String serverId = message.getString("serverId");
                    String serverAddress = message.getString("serverAddress");
                    int serverPort = message.getInt("serverPort");
                    String syncAddress = message.getString("syncAddress");
                    discoveryService.processServerAnnouncement(serverId, serverAddress, serverPort, syncAddress);
                    break;

                case "SERVER_PING":
                    String fromServerId = message.getString("fromServer");
                    JSONObject pingResponse = discoveryService.processServerPing(fromServerId);
                    response = pingResponse;
                    break;

                case "IS_COORDINATOR_REQUEST":
                    response.put("isCoordinator", serverState.isCoordinator());
                    break;

                case "DATA_REPLICATION":
                    logger.log("=== RECEBIDA MENSAGEM DE REPLICAÇÃO ===");
                    logger.log("ReplicationService disponível: " + (replicationService != null));

                    if (replicationService != null) {
                        String sourceServerId = message.getString("sourceServerId");
                        String eventType = message.getString("eventType");
                        String entityId = message.getString("entityId");
                        long replicationTimestamp = message.getLong("timestamp");
                        JSONObject eventData = message.getJSONObject("data");

                        logger.log("Processando replicação: " + eventType + " de " + sourceServerId);

                        JSONObject replicationResponse = replicationService.processReplicationEvent(
                                sourceServerId, eventType, entityId, replicationTimestamp, eventData);

                        response = replicationResponse;
                    } else {
                        logger.logError("ERRO: DataReplicationService é NULL no ServerCommunication!", null);
                        response.put("success", false);
                        response.put("error", "Serviço de replicação não disponível");
                    }
                    break;

                default:
                    response.put("success", false);
                    response.put("error", "Ação desconhecida: " + action);
            }

            response.put("logicalTime", TimeManager.getInstance().getLogicalTime());

            return response.toString();
        } catch (Exception e) {
            logger.logError("Erro ao processar mensagem", e);

            JSONObject errorResponse = new JSONObject();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());

            return errorResponse.toString();
        }
    }

    public void sendMessage(String serverId, String message) {
        senderExecutor.submit(() -> {
            try {
                Long lastAttempt = lastConnectionAttempt.get(serverId);
                long now = System.currentTimeMillis();
                if (lastAttempt != null && now - lastAttempt < CONNECTION_RETRY_INTERVAL_MS) {
                    logger.log("Ignorando tentativa de conexão para " + serverId +
                            " (última tentativa: " + (now - lastAttempt) + "ms atrás)");
                    return;
                }

                ServerState.ServerInfo serverInfo = serverState.getKnownServers().get(serverId);
                if (serverInfo == null) {
                    logger.log("Servidor desconhecido: " + serverId);
                    return;
                }

                JSONObject jsonMessage = new JSONObject(message);
                jsonMessage.put("logicalTime", TimeManager.getInstance().incrementLogicalClock());

                String serverAddress = "tcp://" + serverInfo.getAddress() + ":" + serverInfo.getPort();
                try (ZContext tempContext = new ZContext()) {
                    ZMQ.Socket socket = tempContext.createSocket(SocketType.REQ);
                    socket.setSendTimeOut(2000);
                    socket.setReceiveTimeOut(2000);
                    socket.connect(serverAddress);

                    socket.send(jsonMessage.toString().getBytes(StandardCharsets.UTF_8));
                    logger.log("Mensagem enviada para " + serverId + ": " + jsonMessage);

                    byte[] responseBytes = socket.recv();
                    if (responseBytes != null) {
                        String responseStr = new String(responseBytes, StandardCharsets.UTF_8);
                        logger.log("Resposta recebida de " + serverId + ": " + responseStr);

                        JSONObject response = new JSONObject(responseStr);
                        if (response.has("logicalTime")) {
                            long receivedLogicalTime = response.getLong("logicalTime");
                            TimeManager.getInstance().updateLogicalClock(receivedLogicalTime);
                        }

                        if (!serverInfo.isActive()) {
                            serverState.setServerActive(serverId, true);
                            logger.log("Servidor " + serverId + " agora está ativo");
                        }
                    } else {
                        lastConnectionAttempt.put(serverId, now);
                        if (serverInfo.isActive()) {
                            serverState.setServerActive(serverId, false);
                            logger.log("Servidor " + serverId + " não está respondendo");
                        }
                    }
                }
            } catch (Exception e) {
                logger.logError("Erro ao enviar mensagem para " + serverId, e);
                lastConnectionAttempt.put(serverId, System.currentTimeMillis());

                ServerState.ServerInfo serverInfo = serverState.getKnownServers().get(serverId);
                if (serverInfo != null && serverInfo.isActive()) {
                    serverState.setServerActive(serverId, false);
                    logger.log("Servidor " + serverId + " marcado como inativo devido a erro de comunicação");
                }
            }
        });
    }

    public String sendMessageWithResponse(String serverId, String message) throws Exception {
        // Obtém informações do servidor
        ServerState.ServerInfo serverInfo = serverState.getKnownServers().get(serverId);
        if (serverInfo == null) {
            throw new IllegalArgumentException("Servidor desconhecido: " + serverId);
        }

        JSONObject jsonMessage = new JSONObject(message);
        jsonMessage.put("logicalTime", TimeManager.getInstance().incrementLogicalClock());

        String serverAddress = "tcp://" + serverInfo.getAddress() + ":" + serverInfo.getPort();
        try (ZContext tempContext = new ZContext()) {
            ZMQ.Socket socket = tempContext.createSocket(SocketType.REQ);
            socket.setSendTimeOut(3000);
            socket.setReceiveTimeOut(3000);
            socket.connect(serverAddress);

            socket.send(jsonMessage.toString().getBytes(StandardCharsets.UTF_8));
            logger.log("Mensagem enviada para " + serverId + ": " + jsonMessage);

            byte[] responseBytes = socket.recv();
            if (responseBytes == null) {
                lastConnectionAttempt.put(serverId, System.currentTimeMillis());
                if (serverInfo.isActive()) {
                    serverState.setServerActive(serverId, false);
                }
                throw new Exception("Timeout ao aguardar resposta do servidor " + serverId);
            }

            if (!serverInfo.isActive()) {
                serverState.setServerActive(serverId, true);
                logger.log("Servidor " + serverId + " agora está ativo");
            }

            String responseStr = new String(responseBytes, StandardCharsets.UTF_8);
            logger.log("Resposta recebida de " + serverId + ": " + responseStr);

            JSONObject response = new JSONObject(responseStr);
            if (response.has("logicalTime")) {
                long receivedLogicalTime = response.getLong("logicalTime");
                TimeManager.getInstance().updateLogicalClock(receivedLogicalTime);
            }

            return responseStr;
        }
    }

    public void broadcastToActiveServers(String message) {
        Set<String> activeServers = serverState.getActiveServerIds();
        for (String serverId : activeServers) {
            if (!serverId.equals(serverState.getServerId())) {
                sendMessage(serverId, message);
            }
        }
    }

    public String getSyncBindAddress() {
        return syncBindAddress;
    }
}