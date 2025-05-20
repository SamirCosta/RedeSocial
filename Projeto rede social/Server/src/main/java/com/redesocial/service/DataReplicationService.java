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

/**
 * Serviço responsável pela replicação de dados entre servidores
 */
public class DataReplicationService {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final Queue<ReplicationEvent> pendingReplications;
    private final ExecutorService executor;
    private final AtomicBoolean running;

    /**
     * Construtor do serviço de replicação de dados
     *
     * @param serverState Estado do servidor
     * @param logger Logger de eventos
     * @param communication Comunicação entre servidores
     */
    public DataReplicationService(ServerState serverState, EventLogger logger, ServerCommunication communication) {
        this.serverState = serverState;
        this.logger = logger;
        this.communication = communication;
        this.pendingReplications = new ConcurrentLinkedQueue<>();
        this.executor = Executors.newSingleThreadExecutor();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Inicia o serviço de replicação
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.log("Iniciando serviço de replicação de dados");
            executor.submit(this::processReplicationQueue);
        }
    }

    /**
     * Para o serviço de replicação
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            executor.shutdown();
            logger.log("Serviço de replicação de dados parado");
        }
    }

    /**
     * Adiciona um evento de replicação à fila
     *
     * @param event Evento a ser replicado
     */
    public void queueReplicationEvent(ReplicationEvent event) {
        pendingReplications.offer(event);
        logger.log("Evento de replicação adicionado à fila: " + event.getType());
    }

    /**
     * Processa a fila de eventos de replicação
     */
    private void processReplicationQueue() {
        while (running.get()) {
            try {
                ReplicationEvent event = pendingReplications.poll();
                if (event != null) {
                    replicateToAllServers(event);
                }

                // Pequena pausa para evitar consumo excessivo de CPU
                Thread.sleep(50);
            } catch (Exception e) {
                logger.logError("Erro ao processar fila de replicação", e);
            }
        }
    }

    /**
     * Replica um evento para todos os servidores ativos
     *
     * @param event Evento a ser replicado
     */
    private void replicateToAllServers(ReplicationEvent event) {
        // Cria mensagem de replicação
        JSONObject replicationMessage = new JSONObject();
        replicationMessage.put("action", "DATA_REPLICATION");
        replicationMessage.put("sourceServerId", serverState.getServerId());
        replicationMessage.put("eventType", event.getType());
        replicationMessage.put("entityId", event.getEntityId());
        replicationMessage.put("timestamp", event.getTimestamp());
        replicationMessage.put("data", event.getData());

        logger.log("Replicando evento " + event.getType() + " para outros servidores");

        // Envia para todos os servidores ativos, exceto este E exceto balanceadores
        for (String serverId : serverState.getActiveDataServers()) {
            if (!serverId.equals(serverState.getServerId())) {
                communication.sendMessage(serverId, replicationMessage.toString());
            }
        }
    }

    /**
     * Processa um evento de replicação recebido de outro servidor
     *
     * @param sourceServerId ID do servidor que originou o evento
     * @param eventType Tipo do evento
     * @param entityId ID da entidade
     * @param timestamp Timestamp do evento
     * @param data Dados do evento
     * @return Resposta indicando sucesso ou falha
     */
    public JSONObject processReplicationEvent(String sourceServerId, String eventType,
                                              String entityId, long timestamp, JSONObject data) {
        logger.log("Processando evento de replicação do tipo " + eventType +
                " do servidor " + sourceServerId);

        JSONObject response = new JSONObject();
        response.put("success", true);

        try {
            // Delega o processamento para o ReplicationManager
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