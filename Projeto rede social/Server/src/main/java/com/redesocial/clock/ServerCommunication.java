package com.redesocial.clock;

import com.redesocial.model.ServerState;
import com.redesocial.service.DataReplicationService;
import com.redesocial.service.ServerDiscoveryService;
import com.redesocial.util.EventLogger;
import com.redesocial.clock.TimeManager;
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

/**
 * Responsável pela comunicação entre servidores para sincronização de relógios
 * e eleição de coordenador.
 */
public class ServerCommunication {
    private final ServerState serverState;
    private final EventLogger logger;
    private final String syncBindAddress;
    private final int syncPort;
    private final ZContext context;
    private final ExecutorService receiverExecutor;
    private final ExecutorService senderExecutor;
    private final AtomicBoolean running;

    // Componentes para sincronização
    private BerkeleyAlgorithm berkeleyAlgorithm;
    private BullyElection bullyElection;
    private ServerDiscoveryService discoveryService;
    private DataReplicationService replicationService;

    // Mapa para armazenar sockets de envio ativos
    private final Map<String, Long> lastConnectionAttempt = new ConcurrentHashMap<>();
    private static final long CONNECTION_RETRY_INTERVAL_MS = 10000; // 10 segundos

    /**
     * Construtor para comunicação entre servidores
     *
     * @param serverState Estado do servidor
     * @param logger Logger de eventos
     * @param syncPort Porta para comunicação de sincronização
     */
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

    /**
     * Inicializa os componentes de sincronização
     *
     * @param syncIntervalMs Intervalo de sincronização de relógios
     * @param coordinatorCheckIntervalMs Intervalo de verificação de coordenador
     */
    public void initializeSynchronization(long syncIntervalMs, long coordinatorCheckIntervalMs) {
        // Inicializa algoritmo de Berkeley
        this.berkeleyAlgorithm = new BerkeleyAlgorithm(
                serverState, logger, this, syncIntervalMs
        );

        // Inicializa algoritmo de eleição Bully
        this.bullyElection = new BullyElection(
                serverState, logger, this, coordinatorCheckIntervalMs
        );

        // Inicializa serviço de descoberta de servidores
        this.discoveryService = new ServerDiscoveryService(
                serverState, logger, this, 15000 // 15 segundos
        );
    }

    /**
     * Inicia a comunicação entre servidores
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            // Inicia receptor de mensagens
            receiverExecutor.submit(this::receiveMessages);

            // Inicializa o gerenciador de tempo
            try {
                if (TimeManager.getInstance() == null) {
                    TimeManager.initialize(serverState, logger);
                }
            } catch (Exception e) {
                // TimeManager pode não ter sido inicializado ainda
                TimeManager.initialize(serverState, logger);
            }

            // Espera um pouco antes de iniciar o resto dos serviços
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Inicia serviços em background com pequenos atrasos para evitar race conditions
            senderExecutor.submit(() -> {
                try {
                    Thread.sleep(2000);
                    // Inicia verificação de coordenador
                    bullyElection.startCoordinatorCheck();

                    Thread.sleep(1000);
                    // Inicia sincronização de relógios
                    berkeleyAlgorithm.startSynchronization();

                    Thread.sleep(1000);
                    // Inicia descoberta de servidores
                    discoveryService.start();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            logger.log("Comunicação entre servidores iniciada em " + syncBindAddress);
        }
    }

    /**
     * Para a comunicação entre servidores
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.log("Parando comunicação entre servidores...");

            // Para sincronização de relógios
            try {
                berkeleyAlgorithm.stopSynchronization();
            } catch (Exception e) {
                logger.logError("Erro ao parar algoritmo de Berkeley", e);
            }

            // Para verificação de coordenador
            try {
                bullyElection.stopCoordinatorCheck();
            } catch (Exception e) {
                logger.logError("Erro ao parar algoritmo de eleição", e);
            }

            // Para descoberta de servidores
            try {
                discoveryService.stop();
            } catch (Exception e) {
                logger.logError("Erro ao parar serviço de descoberta", e);
            }

            // Para threads
            shutdownExecutorService(receiverExecutor, "Receptor");
            shutdownExecutorService(senderExecutor, "Sender");

            // Fecha contexto ZeroMQ
            try {
                context.close();
            } catch (Exception e) {
                logger.logError("Erro ao fechar contexto ZeroMQ", e);
            }

            logger.log("Comunicação entre servidores parada");
        }
    }

    /**
     * Encerra um ExecutorService de forma segura
     *
     * @param executor O executor a ser encerrado
     * @param name Nome do executor para log
     */
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

    /**
     * Loop principal para receber mensagens de outros servidores
     */
    private void receiveMessages() {
        ZMQ.Socket socket = null;
        boolean bindSuccessful = false;

        try {
            // Socket para receber mensagens de sincronização
            socket = context.createSocket(SocketType.REP);

            // Configura timeout para a operação de bind
            socket.setSendTimeOut(3000);
            socket.setReceiveTimeOut(1000);

            // Tenta fazer o bind com até 5 tentativas
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
                    if (attempt < 4) { // Não aguardar após a última tentativa
                        try {
                            Thread.sleep(1000 * (attempt + 1)); // Backoff exponencial
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
                    // Recebe mensagem com timeout
                    byte[] messageBytes = socket.recv();
                    if (messageBytes == null) {
                        // Sem mensagem, continua o loop
                        continue;
                    }

                    String messageStr = new String(messageBytes, StandardCharsets.UTF_8);
                    logger.log("Mensagem recebida: " + messageStr);

                    // Processa a mensagem e envia resposta
                    String response = processMessage(messageStr);
                    socket.send(response.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    if (running.get()) { // Só loga erros se o serviço ainda estiver rodando
                        logger.logError("Erro ao processar mensagem recebida", e);
                    }
                }
            }
        } catch (Exception e) {
            if (running.get()) { // Só loga erros se o serviço ainda estiver rodando
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

            // Atualiza o relógio lógico
            if (message.has("logicalTime")) {
                long receivedLogicalTime = message.getLong("logicalTime");
                TimeManager.getInstance().updateLogicalClock(receivedLogicalTime);
            }

            // Cria resposta padrão
            JSONObject response = new JSONObject();
            response.put("success", true);

            // Processa a ação
            switch (action) {
                case "TIME_REQUEST":
                    // Solicitação de tempo do coordenador
                    String coordinatorId = message.getString("coordinator");
                    long coordinatorTimestamp = message.getLong("timestamp");
                    berkeleyAlgorithm.processTimeRequest(coordinatorId, coordinatorTimestamp);
                    break;

                case "TIME_RESPONSE":
                    // Resposta de tempo de um servidor
                    String respondingServerId = message.getString("serverId");
                    long timeDifference = message.getLong("timeDifference");
                    berkeleyAlgorithm.processTimeResponse(respondingServerId, timeDifference);
                    break;

                case "CLOCK_ADJUSTMENT":
                    // Ajuste de relógio enviado pelo coordenador
                    String adjCoordinatorId = message.getString("coordinator");
                    long adjustment = message.getLong("adjustment");
                    berkeleyAlgorithm.applyClockAdjustment(adjCoordinatorId, adjustment);
                    break;

                case "ELECTION":
                    // Mensagem de início de eleição
                    String electionStarterId = message.getString("fromServer");
                    bullyElection.processElectionMessage(electionStarterId);
                    break;

                case "ELECTION_RESPONSE":
                    // Resposta à mensagem de eleição
                    String electionResponderId = message.getString("fromServer");
                    bullyElection.processElectionResponse(electionResponderId);
                    break;

                case "COORDINATOR":
                    // Anúncio de novo coordenador
                    String newCoordinatorId = message.getString("coordinatorId");
                    bullyElection.processCoordinatorMessage(newCoordinatorId);
                    break;

                case "COORDINATOR_HEARTBEAT":
                    // Heartbeat do coordenador
                    // Apenas confirma recebimento
                    break;

                case "COORDINATOR_PING":
                    // Ping para verificar se o coordenador está ativo
                    // Apenas confirma recebimento
                    break;

                case "SERVER_ANNOUNCEMENT":
                    // Anúncio de servidor na rede
                    String serverId = message.getString("serverId");
                    String serverAddress = message.getString("serverAddress");
                    int serverPort = message.getInt("serverPort");
                    String syncAddress = message.getString("syncAddress");
                    discoveryService.processServerAnnouncement(serverId, serverAddress, serverPort, syncAddress);
                    break;

                case "SERVER_PING":
                    // Ping para verificar se o servidor está ativo
                    String fromServerId = message.getString("fromServer");
                    JSONObject pingResponse = discoveryService.processServerPing(fromServerId);
                    response = pingResponse;
                    break;

                case "IS_COORDINATOR_REQUEST":
                    // Resposta à verificação de coordenador
                    response.put("isCoordinator", serverState.isCoordinator());
                    break;

                case "DATA_REPLICATION":
                    // Replicação de dados
                    String sourceServerId = message.getString("sourceServerId");
                    String eventType = message.getString("eventType");
                    String entityId = message.getString("entityId");
                    long replicationTimestamp = message.getLong("timestamp");
                    JSONObject eventData = message.getJSONObject("data");

                    // Processa a replicação através do DataReplicationService
                    if (replicationService != null) {
                        JSONObject replicationResponse = replicationService.processReplicationEvent(
                                sourceServerId, eventType, entityId, replicationTimestamp, eventData);

                        response = replicationResponse;
                    } else {
                        response.put("success", false);
                        response.put("error", "Serviço de replicação não disponível");
                    }
                    break;

                default:
                    response.put("success", false);
                    response.put("error", "Ação desconhecida: " + action);
            }

            // Adiciona o relógio lógico atual na resposta
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

    /**
     * Envia uma mensagem para um servidor específico
     *
     * @param serverId ID do servidor destinatário
     * @param message Mensagem a ser enviada
     */
    public void sendMessage(String serverId, String message) {
        senderExecutor.submit(() -> {
            try {
                // Verifica se já tentamos conectar recentemente a este servidor sem sucesso
                Long lastAttempt = lastConnectionAttempt.get(serverId);
                long now = System.currentTimeMillis();
                if (lastAttempt != null && now - lastAttempt < CONNECTION_RETRY_INTERVAL_MS) {
                    // Ignora esta tentativa para evitar flood de mensagens para um servidor inativo
                    logger.log("Ignorando tentativa de conexão para " + serverId +
                            " (última tentativa: " + (now - lastAttempt) + "ms atrás)");
                    return;
                }

                // Obtém informações do servidor
                ServerState.ServerInfo serverInfo = serverState.getKnownServers().get(serverId);
                if (serverInfo == null) {
                    logger.log("Servidor desconhecido: " + serverId);
                    return;
                }

                // Adiciona o relógio lógico na mensagem
                JSONObject jsonMessage = new JSONObject(message);
                jsonMessage.put("logicalTime", TimeManager.getInstance().incrementLogicalClock());

                // Envia a mensagem
                String serverAddress = "tcp://" + serverInfo.getAddress() + ":" + serverInfo.getPort();
                try (ZContext tempContext = new ZContext()) {
                    ZMQ.Socket socket = tempContext.createSocket(SocketType.REQ);
                    socket.setSendTimeOut(2000);
                    socket.setReceiveTimeOut(2000);
                    socket.connect(serverAddress);

                    // Envia a mensagem e espera resposta
                    socket.send(jsonMessage.toString().getBytes(StandardCharsets.UTF_8));
                    logger.log("Mensagem enviada para " + serverId + ": " + jsonMessage);

                    // Aguarda resposta (com timeout)
                    byte[] responseBytes = socket.recv();
                    if (responseBytes != null) {
                        String responseStr = new String(responseBytes, StandardCharsets.UTF_8);
                        logger.log("Resposta recebida de " + serverId + ": " + responseStr);

                        // Processa a resposta
                        JSONObject response = new JSONObject(responseStr);
                        if (response.has("logicalTime")) {
                            long receivedLogicalTime = response.getLong("logicalTime");
                            TimeManager.getInstance().updateLogicalClock(receivedLogicalTime);
                        }

                        // Se chegou aqui, a comunicação foi bem-sucedida, então marcamos o servidor como ativo
                        if (!serverInfo.isActive()) {
                            serverState.setServerActive(serverId, true);
                            logger.log("Servidor " + serverId + " agora está ativo");
                        }
                    } else {
                        // Se não recebemos resposta, registramos a tentativa sem sucesso
                        lastConnectionAttempt.put(serverId, now);
                        // E marcamos o servidor como inativo
                        if (serverInfo.isActive()) {
                            serverState.setServerActive(serverId, false);
                            logger.log("Servidor " + serverId + " não está respondendo");
                        }
                    }
                }
            } catch (Exception e) {
                logger.logError("Erro ao enviar mensagem para " + serverId, e);
                // Registra a tentativa sem sucesso
                lastConnectionAttempt.put(serverId, System.currentTimeMillis());

                // Obtém informações do servidor
                ServerState.ServerInfo serverInfo = serverState.getKnownServers().get(serverId);
                if (serverInfo != null && serverInfo.isActive()) {
                    serverState.setServerActive(serverId, false);
                    logger.log("Servidor " + serverId + " marcado como inativo devido a erro de comunicação");
                }
            }
        });
    }

    /**
     * Envia uma mensagem para um servidor específico e aguarda a resposta
     *
     * @param serverId ID do servidor destinatário
     * @param message Mensagem a ser enviada
     * @return Resposta do servidor
     * @throws Exception Se ocorrer erro na comunicação
     */
    public String sendMessageWithResponse(String serverId, String message) throws Exception {
        // Obtém informações do servidor
        ServerState.ServerInfo serverInfo = serverState.getKnownServers().get(serverId);
        if (serverInfo == null) {
            throw new IllegalArgumentException("Servidor desconhecido: " + serverId);
        }

        // Adiciona o relógio lógico na mensagem
        JSONObject jsonMessage = new JSONObject(message);
        jsonMessage.put("logicalTime", TimeManager.getInstance().incrementLogicalClock());

        // Envia a mensagem
        String serverAddress = "tcp://" + serverInfo.getAddress() + ":" + serverInfo.getPort();
        try (ZContext tempContext = new ZContext()) {
            ZMQ.Socket socket = tempContext.createSocket(SocketType.REQ);
            socket.setSendTimeOut(3000); // Timeout de 3 segundos
            socket.setReceiveTimeOut(3000); // Timeout de 3 segundos
            socket.connect(serverAddress);

            // Envia a mensagem e espera resposta
            socket.send(jsonMessage.toString().getBytes(StandardCharsets.UTF_8));
            logger.log("Mensagem enviada para " + serverId + ": " + jsonMessage);

            // Aguarda resposta (com timeout)
            byte[] responseBytes = socket.recv();
            if (responseBytes == null) {
                // Registra a tentativa sem sucesso
                lastConnectionAttempt.put(serverId, System.currentTimeMillis());
                // E marca o servidor como inativo
                if (serverInfo.isActive()) {
                    serverState.setServerActive(serverId, false);
                }
                throw new Exception("Timeout ao aguardar resposta do servidor " + serverId);
            }

            // Se chegou aqui, a comunicação foi bem-sucedida
            if (!serverInfo.isActive()) {
                serverState.setServerActive(serverId, true);
                logger.log("Servidor " + serverId + " agora está ativo");
            }

            String responseStr = new String(responseBytes, StandardCharsets.UTF_8);
            logger.log("Resposta recebida de " + serverId + ": " + responseStr);

            // Processa a resposta
            JSONObject response = new JSONObject(responseStr);
            if (response.has("logicalTime")) {
                long receivedLogicalTime = response.getLong("logicalTime");
                TimeManager.getInstance().updateLogicalClock(receivedLogicalTime);
            }

            return responseStr;
        }
    }

    /**
     * Envia mensagem para todos os servidores ativos
     *
     * @param message Mensagem a ser enviada
     */
    public void broadcastToActiveServers(String message) {
        Set<String> activeServers = serverState.getActiveServerIds();
        for (String serverId : activeServers) {
            if (!serverId.equals(serverState.getServerId())) {
                sendMessage(serverId, message);
            }
        }
    }

    /**
     * Obtém o endereço de sincronização atual
     *
     * @return Endereço de sincronização
     */
    public String getSyncBindAddress() {
        return syncBindAddress;
    }
}