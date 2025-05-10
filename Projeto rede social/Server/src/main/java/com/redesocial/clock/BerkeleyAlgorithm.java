package com.redesocial.clock;

import com.redesocial.model.ServerState;
import com.redesocial.util.EventLogger;
import com.redesocial.clock.TimeManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementação do algoritmo de Berkeley para sincronização de relógios
 * entre servidores distribuídos.
 */
public class BerkeleyAlgorithm {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final ScheduledExecutorService scheduler;
    private final long syncIntervalMs;

    // Período de espera para coletar respostas dos servidores (em ms)
    private final long waitForResponsesMs = 3000;

    // Armazena as diferenças de tempo recebidas dos servidores
    private Map<String, Long> timeDifferences;

    /**
     * Construtor para o algoritmo de Berkeley
     *
     * @param serverState Estado do servidor
     * @param logger Logger de eventos
     * @param communication Comunicação entre servidores
     * @param syncIntervalMs Intervalo de sincronização em milissegundos
     */
    public BerkeleyAlgorithm(ServerState serverState, EventLogger logger,
                             ServerCommunication communication, long syncIntervalMs) {
        this.serverState = serverState;
        this.logger = logger;
        this.communication = communication;
        this.syncIntervalMs = syncIntervalMs;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.timeDifferences = new HashMap<>();
    }

    /**
     * Inicia a sincronização periódica se este servidor for o coordenador
     */
    public void startSynchronization() {
        logger.log("Iniciando agendamento de sincronização de relógios");

        // Agenda execução periódica da sincronização
        scheduler.scheduleAtFixedRate(() -> {
            if (serverState.isCoordinator()) {
                logger.log("Iniciando sincronização de relógios como coordenador");
                initiateTimeSync();
            }
        }, 0, syncIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Para a sincronização periódica
     */
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

    /**
     * Inicia uma rodada de sincronização (executado pelo coordenador)
     */
    public void initiateTimeSync() {
        // Limpa as diferenças de tempo anteriores
        timeDifferences.clear();

        // Obtém os servidores ativos
        Set<String> activeServers = serverState.getActiveServerIds();

        if (activeServers.isEmpty()) {
            logger.log("Não há outros servidores ativos para sincronizar");
            return;
        }

        logger.log("Iniciando sincronização com " + activeServers.size() + " servidores");

        // Inclui o próprio servidor na lista
        long localTime = TimeManager.getInstance().getPhysicalTime();
        timeDifferences.put(serverState.getServerId(), 0L); // Diferença zero para o próprio servidor

        // Envia solicitação de tempo para todos os servidores ativos
        JSONObject timeRequest = new JSONObject();
        timeRequest.put("action", "TIME_REQUEST");
        timeRequest.put("coordinator", serverState.getServerId());
        timeRequest.put("timestamp", localTime);

        for (String serverId : activeServers) {
            if (!serverId.equals(serverState.getServerId())) {
                communication.sendMessage(serverId, timeRequest.toString());
            }
        }

        // Agenda o cálculo após um período de espera
        scheduler.schedule(this::calculateAverageOffset, waitForResponsesMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Processa uma solicitação de tempo recebida de um coordenador
     *
     * @param coordinatorId ID do servidor coordenador
     * @param coordinatorTimestamp Timestamp enviado pelo coordenador
     */
    public void processTimeRequest(String coordinatorId, long coordinatorTimestamp) {
        // Se não reconhecemos este servidor como coordenador, ignoramos
        if (!serverState.isCoordinator() ||
                !coordinatorId.equals(serverState.getServerId())) {

            // Obtém o tempo local
            long localTime = TimeManager.getInstance().getPhysicalTime();

            // Calcula a diferença entre o tempo local e o do coordenador
            long timeDifference = localTime - coordinatorTimestamp;

            logger.log("Recebida solicitação de tempo do coordenador " + coordinatorId +
                    ". Diferença: " + timeDifference + "ms");

            // Envia a resposta de volta ao coordenador
            JSONObject timeResponse = new JSONObject();
            timeResponse.put("action", "TIME_RESPONSE");
            timeResponse.put("serverId", serverState.getServerId());
            timeResponse.put("requestTimestamp", coordinatorTimestamp);
            timeResponse.put("responseTimestamp", localTime);
            timeResponse.put("timeDifference", timeDifference);

            communication.sendMessage(coordinatorId, timeResponse.toString());
        }
    }

    /**
     * Processa uma resposta de tempo recebida de outro servidor
     *
     * @param serverId ID do servidor que respondeu
     * @param timeDifference Diferença de tempo reportada pelo servidor
     */
    public void processTimeResponse(String serverId, long timeDifference) {
        if (serverState.isCoordinator()) {
            logger.log("Recebida resposta de tempo do servidor " + serverId +
                    ". Diferença: " + timeDifference + "ms");

            // Armazena a diferença de tempo
            timeDifferences.put(serverId, timeDifference);
        }
    }

    /**
     * Calcula o offset médio e notifica os servidores (executado pelo coordenador)
     */
    private void calculateAverageOffset() {
        if (timeDifferences.isEmpty() || !serverState.isCoordinator()) {
            return;
        }

        logger.log("Calculando ajuste de relógio com " + timeDifferences.size() + " amostras");

        // Calcula a média das diferenças
        long sum = 0;
        for (long diff : timeDifferences.values()) {
            sum += diff;
        }
        long averageOffset = sum / timeDifferences.size();

        logger.log("Offset médio calculado: " + averageOffset + "ms");

        // Atualiza o offset do coordenador
        TimeManager timeManager = TimeManager.getInstance();
        long currentOffset = timeManager.getClockOffset();
        long newOffset = currentOffset - averageOffset;
        timeManager.updateClockOffset(newOffset);

        // Notifica cada servidor sobre seu ajuste
        for (Map.Entry<String, Long> entry : timeDifferences.entrySet()) {
            String serverId = entry.getKey();
            long serverDiff = entry.getValue();

            // O ajuste para cada servidor é a diferença entre seu offset e a média
            long adjustment = averageOffset - serverDiff;

            // Não precisa enviar para o próprio coordenador
            if (!serverId.equals(serverState.getServerId())) {
                sendClockAdjustment(serverId, adjustment);
            }
        }
    }

    /**
     * Envia o ajuste de relógio para um servidor específico
     *
     * @param serverId ID do servidor a receber o ajuste
     * @param adjustment Valor do ajuste a ser aplicado
     */
    private void sendClockAdjustment(String serverId, long adjustment) {
        logger.log("Enviando ajuste de relógio para servidor " + serverId +
                ": " + adjustment + "ms");

        JSONObject adjustmentMessage = new JSONObject();
        adjustmentMessage.put("action", "CLOCK_ADJUSTMENT");
        adjustmentMessage.put("coordinator", serverState.getServerId());
        adjustmentMessage.put("adjustment", adjustment);

        communication.sendMessage(serverId, adjustmentMessage.toString());
    }

    /**
     * Aplica um ajuste de relógio recebido do coordenador
     *
     * @param coordinatorId ID do servidor coordenador
     * @param adjustment Valor do ajuste a ser aplicado
     */
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