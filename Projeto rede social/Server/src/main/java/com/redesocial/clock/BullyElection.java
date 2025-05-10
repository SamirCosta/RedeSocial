package com.redesocial.clock;

import com.redesocial.model.ServerState;
import com.redesocial.util.EventLogger;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementação do algoritmo de eleição Bully para escolha de coordenador
 * entre servidores distribuídos.
 */
public class BullyElection {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final ScheduledExecutorService scheduler;

    // Tempo de espera para respostas durante eleição (ms)
    private final long electionTimeoutMs = 5000;

    // Intervalo para verificação de coordenador (heartbeat)
    private final long coordinatorCheckIntervalMs;

    // Conjunto de servidores que responderam durante uma eleição
    private final Set<String> respondedServers;

    // Flag para indicar se uma eleição está em andamento
    private final AtomicBoolean electionInProgress;

    /**
     * Construtor para o algoritmo de eleição Bully
     *
     * @param serverState Estado do servidor
     * @param logger Logger de eventos
     * @param communication Comunicação entre servidores
     * @param coordinatorCheckIntervalMs Intervalo para verificação do coordenador em ms
     */
    public BullyElection(ServerState serverState, EventLogger logger,
                         ServerCommunication communication, long coordinatorCheckIntervalMs) {
        this.serverState = serverState;
        this.logger = logger;
        this.communication = communication;
        this.coordinatorCheckIntervalMs = coordinatorCheckIntervalMs;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.respondedServers = new HashSet<>();
        this.electionInProgress = new AtomicBoolean(false);
    }

    /**
     * Inicia a verificação periódica do coordenador
     */
    public void startCoordinatorCheck() {
        logger.log("Iniciando verificação periódica de coordenador");

        // Agenda verificação periódica do coordenador
        scheduler.scheduleAtFixedRate(() -> {
            checkCoordinator();
        }, coordinatorCheckIntervalMs, coordinatorCheckIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Para a verificação periódica do coordenador
     */
    public void stopCoordinatorCheck() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Verifica se o coordenador atual está ativo, iniciando uma eleição se necessário
     */
    private void checkCoordinator() {
        // Verifica se já somos o coordenador
        if (serverState.isCoordinator()) {
            // Envia heartbeat para todos os servidores ativos
            sendCoordinatorHeartbeat();
            return;
        }

        // Obtém o atual coordenador dos servidores conhecidos
        String currentCoordinator = findCurrentCoordinator();

        // Se não encontrar um coordenador, inicia uma eleição
        if (currentCoordinator == null) {
            logger.log("Nenhum coordenador encontrado. Iniciando eleição.");
            startElection();
        } else {
            // Envia ping para o coordenador para verificar se está ativo
            logger.log("Verificando se o coordenador " + currentCoordinator + " está ativo");
            sendPingToCoordinator(currentCoordinator);
        }
    }

    /**
     * Procura por um coordenador entre os servidores conhecidos
     *
     * @return ID do coordenador, ou null se não encontrar
     */
    private String findCurrentCoordinator() {
        Map<String, ServerState.ServerInfo> knownServers = serverState.getKnownServers();

        // Procura entre os servidores conhecidos
        for (Map.Entry<String, ServerState.ServerInfo> entry : knownServers.entrySet()) {
            String serverId = entry.getKey();
            ServerState.ServerInfo serverInfo = entry.getValue();

            // Verifica com o servidor se ele é o coordenador
            if (serverInfo.isActive()) {
                JSONObject isCoordinatorRequest = new JSONObject();
                isCoordinatorRequest.put("action", "IS_COORDINATOR_REQUEST");
                isCoordinatorRequest.put("fromServer", serverState.getServerId());

                // Envia a mensagem de forma síncrona para obter resposta
                try {
                    String response = communication.sendMessageWithResponse(serverId, isCoordinatorRequest.toString());
                    JSONObject jsonResponse = new JSONObject(response);

                    if (jsonResponse.getBoolean("isCoordinator")) {
                        return serverId;
                    }
                } catch (Exception e) {
                    logger.logError("Erro ao verificar se " + serverId + " é coordenador", e);
                }
            }
        }

        return null;
    }

    /**
     * Envia uma mensagem de ping para o coordenador atual
     *
     * @param coordinatorId ID do servidor coordenador
     */
    private void sendPingToCoordinator(String coordinatorId) {
        JSONObject pingMessage = new JSONObject();
        pingMessage.put("action", "COORDINATOR_PING");
        pingMessage.put("fromServer", serverState.getServerId());

        try {
            String response = communication.sendMessageWithResponse(coordinatorId, pingMessage.toString());

            // Se conseguir resposta, o coordenador está ativo
            logger.log("Coordenador " + coordinatorId + " respondeu ao ping");
        } catch (Exception e) {
            logger.log("Coordenador " + coordinatorId + " não respondeu ao ping. Iniciando eleição.");
            startElection();
        }
    }

    /**
     * Envia heartbeat para todos os servidores ativos (executado pelo coordenador)
     */
    private void sendCoordinatorHeartbeat() {
        Set<String> activeServers = serverState.getActiveServerIds();

        JSONObject heartbeatMessage = new JSONObject();
        heartbeatMessage.put("action", "COORDINATOR_HEARTBEAT");
        heartbeatMessage.put("coordinatorId", serverState.getServerId());

        for (String serverId : activeServers) {
            if (!serverId.equals(serverState.getServerId())) {
                communication.sendMessage(serverId, heartbeatMessage.toString());
            }
        }

        logger.log("Enviado heartbeat como coordenador para " + (activeServers.size() - 1) + " servidores");
    }

    /**
     * Inicia uma eleição de coordenador
     */
    public void startElection() {
        // Evita iniciar múltiplas eleições simultaneamente
        if (!electionInProgress.compareAndSet(false, true)) {
            logger.log("Eleição já em andamento, ignorando nova solicitação");
            return;
        }

        logger.log("Iniciando eleição de coordenador");
        respondedServers.clear();

        // Obtém todos os servidores com ID maior
        Set<String> higherServers = getHigherIdServers();

        if (higherServers.isEmpty()) {
            // Se não houver servidores com ID maior, este servidor se elege como coordenador
            declareAsCoordinator();
        } else {
            // Envia mensagens de eleição para todos os servidores com ID maior
            sendElectionMessages(higherServers);

            // Agenda verificação de respostas após timeout
            scheduler.schedule(() -> {
                checkElectionResponses(higherServers);
            }, electionTimeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Obtém o conjunto de servidores com ID maior que o atual
     *
     * @return Conjunto de IDs de servidores com ID maior
     */
    private Set<String> getHigherIdServers() {
        Set<String> higherServers = new HashSet<>();
        String currentId = serverState.getServerId();

        for (String serverId : serverState.getActiveServerIds()) {
            if (serverId.compareTo(currentId) > 0) {
                higherServers.add(serverId);
            }
        }

        return higherServers;
    }

    /**
     * Envia mensagens de eleição para os servidores especificados
     *
     * @param serverIds Conjunto de IDs de servidores
     */
    private void sendElectionMessages(Set<String> serverIds) {
        JSONObject electionMessage = new JSONObject();
        electionMessage.put("action", "ELECTION");
        electionMessage.put("fromServer", serverState.getServerId());

        for (String serverId : serverIds) {
            communication.sendMessage(serverId, electionMessage.toString());
            logger.log("Enviada mensagem de eleição para " + serverId);
        }
    }

    /**
     * Processa uma mensagem de eleição recebida de outro servidor
     *
     * @param senderId ID do servidor que enviou a mensagem
     */
    public void processElectionMessage(String senderId) {
        logger.log("Recebida mensagem de eleição de " + senderId);

        // Responde ao remetente para informar que este servidor está ativo
        JSONObject responseMessage = new JSONObject();
        responseMessage.put("action", "ELECTION_RESPONSE");
        responseMessage.put("fromServer", serverState.getServerId());

        communication.sendMessage(senderId, responseMessage.toString());
        logger.log("Enviada resposta de eleição para " + senderId);

        // Inicia uma nova eleição, pois este servidor tem ID maior
        if (serverState.getServerId().compareTo(senderId) > 0) {
            startElection();
        }
    }

    /**
     * Processa uma resposta de eleição recebida de outro servidor
     *
     * @param senderId ID do servidor que enviou a resposta
     */
    public void processElectionResponse(String senderId) {
        logger.log("Recebida resposta de eleição de " + senderId);
        respondedServers.add(senderId);
    }

    /**
     * Verifica as respostas recebidas durante a eleição
     *
     * @param higherServers Conjunto de servidores com ID maior
     */
    private void checkElectionResponses(Set<String> higherServers) {
        // Verifica se algum servidor com ID maior respondeu
        boolean anyResponse = false;
        for (String serverId : higherServers) {
            if (respondedServers.contains(serverId)) {
                anyResponse = true;
                break;
            }
        }

        if (!anyResponse) {
            // Se nenhum servidor com ID maior respondeu, este servidor se elege como coordenador
            declareAsCoordinator();
        } else {
            // Caso contrário, aguarda a mensagem de coordenador
            logger.log("Recebida resposta de servidor com ID maior. Aguardando nova eleição.");
            electionInProgress.set(false);
        }
    }

    /**
     * Declara este servidor como coordenador e notifica os outros
     */
    private void declareAsCoordinator() {
        serverState.setCoordinator(true);
        logger.log("Este servidor foi eleito como coordenador");

        // Envia mensagem de coordenador para todos os servidores ativos
        JSONObject coordinatorMessage = new JSONObject();
        coordinatorMessage.put("action", "COORDINATOR");
        coordinatorMessage.put("coordinatorId", serverState.getServerId());

        for (String serverId : serverState.getActiveServerIds()) {
            if (!serverId.equals(serverState.getServerId())) {
                communication.sendMessage(serverId, coordinatorMessage.toString());
                logger.log("Enviada mensagem de coordenador para " + serverId);
            }
        }

        // Finaliza o processo de eleição
        electionInProgress.set(false);
    }

    /**
     * Processa uma mensagem de coordenador recebida
     *
     * @param coordinatorId ID do servidor coordenador
     */
    public void processCoordinatorMessage(String coordinatorId) {
        logger.log("Recebida mensagem de coordenador de " + coordinatorId);

        // Se este servidor estava se considerando coordenador, mas recebeu mensagem
        // de um servidor com ID maior, aceita o novo coordenador
        if (serverState.isCoordinator() &&
                serverState.getServerId().compareTo(coordinatorId) < 0) {
            serverState.setCoordinator(false);
            logger.log("Este servidor agora reconhece " + coordinatorId + " como coordenador");
        } else if (!serverState.isCoordinator()) {
            logger.log("Este servidor agora reconhece " + coordinatorId + " como coordenador");
        }

        // Finaliza qualquer eleição em andamento
        electionInProgress.set(false);
    }
}