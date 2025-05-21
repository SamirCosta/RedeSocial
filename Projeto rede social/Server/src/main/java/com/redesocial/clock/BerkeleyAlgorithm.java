package com.redesocial.clock;

import com.redesocial.model.ServerState;
import com.redesocial.util.EventLogger;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BerkeleyAlgorithm {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final ScheduledExecutorService scheduler;
    private final long syncIntervalMs;
    private final long waitForResponsesMs = 3000;
    private Map<String, Long> timeDifferences;

    public BerkeleyAlgorithm(ServerState serverState, EventLogger logger,
                             ServerCommunication communication, long syncIntervalMs) {
        this.serverState = serverState;
        this.logger = logger;
        this.communication = communication;
        this.syncIntervalMs = syncIntervalMs;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.timeDifferences = new HashMap<>();
    }

    public void startSynchronization() {
        logger.log("Iniciando agendamento de sincronização de relógios");

        scheduler.scheduleAtFixedRate(() -> {
            if (serverState.isCoordinator()) {
                logger.log("Iniciando sincronização de relógios como coordenador");
                initiateTimeSync();
            }
        }, 0, syncIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void stopSynchronization() {
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

    public void initiateTimeSync() {
        timeDifferences.clear();

        Set<String> activeServers = serverState.getActiveServerIds();

        if (activeServers.isEmpty()) {
            logger.log("Não há outros servidores ativos para sincronizar");
            return;
        }

        logger.log("Iniciando sincronização com " + activeServers.size() + " servidores");

        long localTime = TimeManager.getInstance().getPhysicalTime();
        timeDifferences.put(serverState.getServerId(), 0L);

        JSONObject timeRequest = new JSONObject();
        timeRequest.put("action", "TIME_REQUEST");
        timeRequest.put("coordinator", serverState.getServerId());
        timeRequest.put("timestamp", localTime);

        for (String serverId : activeServers) {
            if (!serverId.equals(serverState.getServerId())) {
                communication.sendMessage(serverId, timeRequest.toString());
            }
        }

        scheduler.schedule(this::calculateAverageOffset, waitForResponsesMs, TimeUnit.MILLISECONDS);
    }

    public void processTimeRequest(String coordinatorId, long coordinatorTimestamp) {
        if (!serverState.isCoordinator() ||
                !coordinatorId.equals(serverState.getServerId())) {

            long localTime = TimeManager.getInstance().getPhysicalTime();

            long timeDifference = localTime - coordinatorTimestamp;

            logger.log("Recebida solicitação de tempo do coordenador " + coordinatorId +
                    ". Diferença: " + timeDifference + "ms");

            JSONObject timeResponse = new JSONObject();
            timeResponse.put("action", "TIME_RESPONSE");
            timeResponse.put("serverId", serverState.getServerId());
            timeResponse.put("requestTimestamp", coordinatorTimestamp);
            timeResponse.put("responseTimestamp", localTime);
            timeResponse.put("timeDifference", timeDifference);

            communication.sendMessage(coordinatorId, timeResponse.toString());
        }
    }

    public void processTimeResponse(String serverId, long timeDifference) {
        if (serverState.isCoordinator()) {
            logger.log("Recebida resposta de tempo do servidor " + serverId +
                    ". Diferença: " + timeDifference + "ms");

            timeDifferences.put(serverId, timeDifference);
        }
    }

    private void calculateAverageOffset() {
        if (timeDifferences.isEmpty() || !serverState.isCoordinator()) {
            return;
        }

        logger.log("Calculando ajuste de relógio com " + timeDifferences.size() + " amostras");

        long sum = 0;
        for (long diff : timeDifferences.values()) {
            sum += diff;
        }
        long averageOffset = sum / timeDifferences.size();

        logger.log("Offset médio calculado: " + averageOffset + "ms");

        TimeManager timeManager = TimeManager.getInstance();
        long currentOffset = timeManager.getClockOffset();
        long newOffset = currentOffset - averageOffset;
        timeManager.updateClockOffset(newOffset);

        for (Map.Entry<String, Long> entry : timeDifferences.entrySet()) {
            String serverId = entry.getKey();
            long serverDiff = entry.getValue();

            long adjustment = averageOffset - serverDiff;

            if (!serverId.equals(serverState.getServerId())) {
                sendClockAdjustment(serverId, adjustment);
            }
        }
    }

    private void sendClockAdjustment(String serverId, long adjustment) {
        logger.log("Enviando ajuste de relógio para servidor " + serverId +
                ": " + adjustment + "ms");

        JSONObject adjustmentMessage = new JSONObject();
        adjustmentMessage.put("action", "CLOCK_ADJUSTMENT");
        adjustmentMessage.put("coordinator", serverState.getServerId());
        adjustmentMessage.put("adjustment", adjustment);

        communication.sendMessage(serverId, adjustmentMessage.toString());
    }

    public void applyClockAdjustment(String coordinatorId, long adjustment) {
        logger.log("Recebido ajuste de relógio do coordenador " + coordinatorId +
                ": " + adjustment + "ms");

        TimeManager timeManager = TimeManager.getInstance();
        long currentOffset = timeManager.getClockOffset();
        long newOffset = currentOffset + adjustment;

        timeManager.updateClockOffset(newOffset);
        logger.log("Relógio ajustado: novo offset = " + newOffset + "ms");
    }
}