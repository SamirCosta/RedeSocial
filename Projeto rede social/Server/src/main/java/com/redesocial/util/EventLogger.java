package com.redesocial.util;

import com.redesocial.model.ServerState;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class EventLogger {
    private final Logger logger;
    private final ServerState serverState;
    private static final Set<String> FILTERED_PATTERNS = new HashSet<>(Arrays.asList(
            "heartbeat", "ping", "verificação", "sincronização", "checking", "IS_COORDINATOR_REQUEST", "\"success\":true"
            ,"CLOCK_ADJUSTMENT", "Recebido ajuste de relógio", "TIME_REQUEST", "TIME_RESPONSE", "Relógio ajustado", "SERVER_ANNOUNCEMENT"
            , "ELECTION", "coordenador", "Relógio atualizado", "COORDINATOR", "Offset", "ajuste de relógio"
    ));

    public EventLogger(ServerState serverState, String logFilePath) throws IOException {
        this.logger = new Logger(logFilePath);
        this.serverState = serverState;

        log("EventLogger initialized for server " + serverState.getServerId());
    }

    public void log(String message) {
        try {
//            for (String pattern : FILTERED_PATTERNS) {
//                if (message.toLowerCase().contains(pattern.toLowerCase())) {
//                    // Log filtrado - apenas retorna sem logar
//                    return;
//                }
//            }

            // Get timestamps
//            long logicalTime = TimeManager.getInstance().getLogicalTime();
//            long physicalTime = TimeManager.getInstance().getAdjustedPhysicalTime();

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            // Format with server ID and timestamps
            String formattedMessage = String.format("[Server %s] %s",
                    serverState.getServerId(),
                    message);

            logger.log(formattedMessage);
        } catch (Exception e) {
            logger.log("[Server " + serverState.getServerId() + "] " + message);
        }
    }

    public void logError(String message, Throwable exception) {
        try {
//            long logicalTime = TimeManager.getInstance().getLogicalTime();
//            long physicalTime = TimeManager.getInstance().getAdjustedPhysicalTime();

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

            String formattedMessage = String.format("[Server %s][L:%s][P:%s] %s",
                    serverState.getServerId(),
                    dataHora,
                    "",
                    message);

            if (exception != null) {
                logger.logError(formattedMessage, exception);
            } else {
                logger.log("ERROR: " + formattedMessage);
            }
        } catch (Exception e) {
            if (exception != null) {
                logger.logError("[Server " + serverState.getServerId() + "] " + message, exception);
            } else {
                logger.log("ERROR: [Server " + serverState.getServerId() + "] " + message);
            }
        }
    }

    public void close() {
        logger.close();
    }
}