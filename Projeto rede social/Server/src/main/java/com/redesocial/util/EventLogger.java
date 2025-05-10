package com.redesocial.util;

import com.redesocial.model.ServerState;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Event logger that includes server information and timestamps in log messages
 */
public class EventLogger {
    private final Logger logger;
    private final ServerState serverState;

    /**
     * Create a new event logger for the specified server
     *
     * @param serverState The server state
     * @param logFilePath The path to the log file
     * @throws IOException If the log file cannot be created or opened
     */
    public EventLogger(ServerState serverState, String logFilePath) throws IOException {
        this.logger = new Logger(logFilePath);
        this.serverState = serverState;

        log("EventLogger initialized for server " + serverState.getServerId());
    }

    /**
     * Log a message with server ID, logical and physical timestamps
     *
     * @param message The message to log
     */
    public void log(String message) {
        try {
            // Get timestamps
//            long logicalTime = TimeManager.getInstance().getLogicalTime();
//            long physicalTime = TimeManager.getInstance().getAdjustedPhysicalTime();

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            // Format with server ID and timestamps
            String formattedMessage = String.format("[Server %s] %s",
                    serverState.getServerId(),
                    message);

            // Log the formatted message
            logger.log(formattedMessage);
        } catch (Exception e) {
            // Fallback if TimeManager is not available yet
            logger.log("[Server " + serverState.getServerId() + "] " + message);
        }
    }

    /**
     * Log an error message with server ID, logical and physical timestamps, and exception details
     *
     * @param message The error message
     * @param exception The exception that occurred
     */
    public void logError(String message, Throwable exception) {
        try {
            // Get timestamps
//            long logicalTime = TimeManager.getInstance().getLogicalTime();
//            long physicalTime = TimeManager.getInstance().getAdjustedPhysicalTime();

            String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

            // Format with server ID and timestamps
            String formattedMessage = String.format("[Server %s][L:%s][P:%s] %s",
                    serverState.getServerId(),
                    dataHora,
                    "",
                    message);

            // Log the formatted error message with exception
            logger.logError(formattedMessage, exception);
        } catch (Exception e) {
            // Fallback if TimeManager is not available yet
            logger.logError("[Server " + serverState.getServerId() + "] " + message, exception);
        }
    }

    /**
     * Close the logger and release resources
     */
    public void close() {
        logger.close();
    }
}