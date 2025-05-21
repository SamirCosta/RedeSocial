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
    private final String dataDirectory;
    private final String[] seedServers;

    public ServerConfig(String configFile) throws IOException {
        properties = new Properties();

        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.load(fis);
        }

        serverId = properties.getProperty("server.id", UUID.randomUUID().toString());

        serverAddress = properties.getProperty("server.address", InetAddress.getLocalHost().getHostAddress());

        serverPort = Integer.parseInt(properties.getProperty("server.port"));

        dataDirectory = properties.getProperty("data.directory", "./data");

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
                ", dataDirectory='" + dataDirectory + '\'' +
                ", seedServers=" + (seedServers.length) +
                '}';
    }
}