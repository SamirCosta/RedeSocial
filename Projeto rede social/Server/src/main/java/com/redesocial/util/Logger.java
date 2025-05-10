package com.redesocial.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private final String logFilePath;
    private final SimpleDateFormat dateFormat;
    private final PrintWriter writer;

    public Logger(String logFilePath) throws IOException {
        this.logFilePath = logFilePath;
        this.dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        this.writer = new PrintWriter(new FileWriter(logFilePath, true), true);
    }

    public synchronized void log(String message) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = timestamp + " - " + message;

        // Write to log file
        writer.println(logEntry);

        // Also print to console
        System.out.println(logEntry);
    }

    public synchronized void logError(String message, Throwable exception) {
        String timestamp = dateFormat.format(new Date());
        String logEntry = timestamp + " - ERROR - " + message;

        // Write to log file
        writer.println(logEntry);
        exception.printStackTrace(writer);

        // Also print to console
        System.err.println(logEntry);
        exception.printStackTrace(System.err);
    }

    public void close() {
        log("Logger closing");
        writer.close();
    }
}