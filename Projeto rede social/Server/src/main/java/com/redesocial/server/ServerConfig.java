package com.redesocial.server;

import com.redesocial.model.ServerState;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;
import java.util.UUID;

public class ServerConfig {
    private final Properties properties;
    private final String serverId;
    private final String serverAddress;
    private final int serverPort;
//    private final int websocketPort;
    private final String dataDirectory;
    private final String[] seedServers;

    public ServerConfig(String configFile) throws IOException {
        properties = new Properties();

        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.load(fis);
        }

        // Generate or load server ID
        serverId = properties.getProperty("server.id", UUID.randomUUID().toString());

        // Get server address (default to local IP)
        serverAddress = properties.getProperty("server.address", InetAddress.getLocalHost().getHostAddress());

        // Get server port
        serverPort = Integer.parseInt(properties.getProperty("server.port"));

        // Get WebSocket port
//        websocketPort = Integer.parseInt(properties.getProperty("websocket.port"));

        // Get data directory
        dataDirectory = properties.getProperty("data.directory", "./data");

        // Get seed servers
        String seedServersStr = properties.getProperty("seed.servers", "");
        seedServers = seedServersStr.isEmpty() ? new String[0] : seedServersStr.split(",");
    }

    public ServerState createServerState() {
        return new ServerState(serverId, serverAddress, serverPort);
    }

    public String getServerId() {
        return serverId;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

//    public int getWebsocketPort() {
//        return websocketPort;
//    }

    public String getDataDirectory() {
        return dataDirectory;
    }

    public String[] getSeedServers() {
        return seedServers;
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public String getLogFilePath() {
        return dataDirectory + "/" + serverId + ".log";
    }

    public String getDataFilePath() {
        return dataDirectory + "/" + serverId + ".data";
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "serverId='" + serverId + '\'' +
                ", serverAddress='" + serverAddress + '\'' +
                ", serverPort=" + serverPort +
//                ", websocketPort=" + websocketPort +
                ", dataDirectory='" + dataDirectory + '\'' +
                ", seedServers=" + (seedServers.length) +
                '}';
    }
}