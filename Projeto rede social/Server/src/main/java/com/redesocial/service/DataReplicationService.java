package com.redesocial.service;

import com.redesocial.clock.ServerCommunication;
import com.redesocial.model.ReplicationEvent;
import com.redesocial.model.ServerState;
import com.redesocial.util.EventLogger;
import org.json.JSONObject;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataReplicationService {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final Queue<ReplicationEvent> pendingReplications;
    private final ExecutorService executor;
    private final AtomicBoolean running;

    public DataReplicationService(ServerState serverState, EventLogger logger, ServerCommunication communication) {
        this.serverState = serverState;
        this.logger = logger;
        this.communication = communication;
        this.pendingReplications = new ConcurrentLinkedQueue<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.log("Iniciando serviço de replicação de dados");
            executor.submit(this::processReplicationQueue);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            executor.shutdown();
            logger.log("Serviço de replicação de dados parado");
        }
    }

    public void queueReplicationEvent(ReplicationEvent event) {
        pendingReplications.offer(event);
        logger.log("Evento de replicação adicionado à fila: " + event.getType());
    }

    private void processReplicationQueue() {
        while (running.get()) {
            try {
                ReplicationEvent event = pendingReplications.poll();
                if (event != null) {
                    replicateToAllServers(event);
                }

                Thread.sleep(50);
            } catch (Exception e) {
                logger.logError("Erro ao processar fila de replicação", e);
            }
        }
    }

    private void replicateToAllServers(ReplicationEvent event) {

        JSONObject replicationMessage = new JSONObject();
        replicationMessage.put("action", "DATA_REPLICATION");
        replicationMessage.put("sourceServerId", serverState.getServerId());
        replicationMessage.put("eventType", event.getType());
        replicationMessage.put("entityId", event.getEntityId());
        replicationMessage.put("timestamp", event.getTimestamp());
        replicationMessage.put("data", event.getData());

        logger.log("Replicando evento " + event.getType() + " para outros servidores");

        for (String serverId : serverState.getActiveDataServers()) {
            if (!serverId.equals(serverState.getServerId())) {
                communication.sendMessage(serverId, replicationMessage.toString());
            }
        }
    }

    public JSONObject processReplicationEvent(String sourceServerId, String eventType,
                                              String entityId, long timestamp, JSONObject data) {
        logger.log("Processando evento de replicação do tipo " + eventType +
                " do servidor " + sourceServerId);

        JSONObject response = new JSONObject();
        response.put("success", true);

        try {

            ReplicationManager.getInstance().handleReplicationEvent(
                    eventType, entityId, timestamp, data);
        } catch (Exception e) {
            logger.logError("Erro ao processar evento de replicação", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }
}