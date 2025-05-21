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

public class BullyElection {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final ScheduledExecutorService scheduler;
    private final long electionTimeoutMs = 5000;
    private final long coordinatorCheckIntervalMs;
    private final Set<String> respondedServers;
    private final AtomicBoolean electionInProgress;

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

    public void startCoordinatorCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            checkCoordinator();
        }, coordinatorCheckIntervalMs, coordinatorCheckIntervalMs, TimeUnit.MILLISECONDS);
    }

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

    private void checkCoordinator() {
        if (serverState.isCoordinator()) {
            sendCoordinatorHeartbeat();
            return;
        }

        String currentCoordinator = findCurrentCoordinator();

        if (currentCoordinator == null) {
            logger.log("Nenhum coordenador encontrado. Iniciando eleição.");
            startElection();
        } else {
            logger.log("Verificando se o coordenador " + currentCoordinator + " está ativo");
            sendPingToCoordinator(currentCoordinator);
        }
    }

    private String findCurrentCoordinator() {
        Map<String, ServerState.ServerInfo> knownServers = serverState.getKnownServers();

        for (Map.Entry<String, ServerState.ServerInfo> entry : knownServers.entrySet()) {
            String serverId = entry.getKey();
            ServerState.ServerInfo serverInfo = entry.getValue();

            if (serverInfo.isActive()) {
                JSONObject isCoordinatorRequest = new JSONObject();
                isCoordinatorRequest.put("action", "IS_COORDINATOR_REQUEST");
                isCoordinatorRequest.put("fromServer", serverState.getServerId());

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

    private void sendPingToCoordinator(String coordinatorId) {
        JSONObject pingMessage = new JSONObject();
        pingMessage.put("action", "COORDINATOR_PING");
        pingMessage.put("fromServer", serverState.getServerId());

        try {
            String response = communication.sendMessageWithResponse(coordinatorId, pingMessage.toString());

            logger.log("Coordenador " + coordinatorId + " respondeu ao ping");
        } catch (Exception e) {
            logger.log("Coordenador " + coordinatorId + " não respondeu ao ping. Iniciando eleição.");
            startElection();
        }
    }

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

    public void startElection() {
        if (!electionInProgress.compareAndSet(false, true)) {
            logger.log("Eleição já em andamento, ignorando nova solicitação");
            return;
        }

        logger.log("Iniciando eleição de coordenador");
        respondedServers.clear();

        Set<String> higherServers = getHigherIdServers();

        if (higherServers.isEmpty()) {
            declareAsCoordinator();
        } else {
            sendElectionMessages(higherServers);

            scheduler.schedule(() -> {
                checkElectionResponses(higherServers);
            }, electionTimeoutMs, TimeUnit.MILLISECONDS);
        }
    }

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

    private void sendElectionMessages(Set<String> serverIds) {
        JSONObject electionMessage = new JSONObject();
        electionMessage.put("action", "ELECTION");
        electionMessage.put("fromServer", serverState.getServerId());

        for (String serverId : serverIds) {
            communication.sendMessage(serverId, electionMessage.toString());
            logger.log("Enviada mensagem de eleição para " + serverId);
        }
    }

    public void processElectionMessage(String senderId) {
        logger.log("Recebida mensagem de eleição de " + senderId);

        JSONObject responseMessage = new JSONObject();
        responseMessage.put("action", "ELECTION_RESPONSE");
        responseMessage.put("fromServer", serverState.getServerId());

        communication.sendMessage(senderId, responseMessage.toString());
        logger.log("Enviada resposta de eleição para " + senderId);

        if (serverState.getServerId().compareTo(senderId) > 0) {
            startElection();
        }
    }

    public void processElectionResponse(String senderId) {
        logger.log("Recebida resposta de eleição de " + senderId);
        respondedServers.add(senderId);
    }

    private void checkElectionResponses(Set<String> higherServers) {

        boolean anyResponse = false;
        for (String serverId : higherServers) {
            if (respondedServers.contains(serverId)) {
                anyResponse = true;
                break;
            }
        }

        if (!anyResponse) {
            declareAsCoordinator();
        } else {
            logger.log("Recebida resposta de servidor com ID maior. Aguardando nova eleição.");
            electionInProgress.set(false);
        }
    }

    private void declareAsCoordinator() {
        serverState.setCoordinator(true);
        logger.log("Este servidor foi eleito como coordenador");

        JSONObject coordinatorMessage = new JSONObject();
        coordinatorMessage.put("action", "COORDINATOR");
        coordinatorMessage.put("coordinatorId", serverState.getServerId());

        for (String serverId : serverState.getActiveServerIds()) {
            if (!serverId.equals(serverState.getServerId())) {
                communication.sendMessage(serverId, coordinatorMessage.toString());
                logger.log("Enviada mensagem de coordenador para " + serverId);
            }
        }

        electionInProgress.set(false);
    }

    public void processCoordinatorMessage(String coordinatorId) {
        logger.log("Recebida mensagem de coordenador de " + coordinatorId);

        if (serverState.isCoordinator() &&
                serverState.getServerId().compareTo(coordinatorId) < 0) {
            serverState.setCoordinator(false);
            logger.log("Este servidor agora reconhece " + coordinatorId + " como coordenador");
        } else if (!serverState.isCoordinator()) {
            logger.log("Este servidor agora reconhece " + coordinatorId + " como coordenador");
        }

        electionInProgress.set(false);
    }
}