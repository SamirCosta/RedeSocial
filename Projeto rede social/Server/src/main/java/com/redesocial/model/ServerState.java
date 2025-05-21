package com.redesocial.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerState implements Serializable {
    private static final long serialVersionUID = 1L;

    private String serverId;
    private String serverAddress;
    private int serverPort;
    private boolean isCoordinator;
    private long clockOffset;
    private Map<String, ServerInfo> knownServers;

    public ServerState(String serverId, String serverAddress, int serverPort) {
        this.serverId = serverId;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.isCoordinator = false;
        this.clockOffset = 0;
        this.knownServers = new ConcurrentHashMap<>();
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

    public boolean isCoordinator() {
        return isCoordinator;
    }

    public void setCoordinator(boolean coordinator) {
        isCoordinator = coordinator;
    }

    public long getClockOffset() {
        return clockOffset;
    }

    public void setClockOffset(long clockOffset) {
        this.clockOffset = clockOffset;
    }

    public void addServer(String serverId, String address, int port, boolean isActive) {
        knownServers.put(serverId, new ServerInfo(serverId, address, port, isActive));
    }

    public void removeServer(String serverId) {
        knownServers.remove(serverId);
    }

    public boolean isBalancer(String serverId) {
        return serverId.equals("balancer") || serverId.startsWith("balancer");
    }

    public void setServerActive(String serverId, boolean active) {
        ServerInfo server = knownServers.get(serverId);
        if (server != null) {
            server.setActive(active);
        }
    }

    public Set<String> getActiveServerIds() {
        Set<String> activeServers = ConcurrentHashMap.newKeySet();
        for (Map.Entry<String, ServerInfo> entry : knownServers.entrySet()) {
            if (entry.getValue().isActive()) {
                activeServers.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(activeServers);
    }

    public Set<String> getActiveDataServers() {
        Set<String> dataServers = ConcurrentHashMap.newKeySet();
        for (Map.Entry<String, ServerInfo> entry : knownServers.entrySet()) {
            String serverId = entry.getKey();
            if (entry.getValue().isActive() && !isBalancer(serverId)) {
                dataServers.add(serverId);
            }
        }
        return Collections.unmodifiableSet(dataServers);
    }

    public Map<String, ServerInfo> getKnownServers() {
        return Collections.unmodifiableMap(knownServers);
    }

    @Override
    public String toString() {
        return "ServerState{" +
                "serverId='" + serverId + '\'' +
                ", serverAddress='" + serverAddress + '\'' +
                ", serverPort=" + serverPort +
                ", isCoordinator=" + isCoordinator +
                ", clockOffset=" + clockOffset +
                ", knownServers=" + knownServers.size() +
                '}';
    }

    public static class ServerInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private String serverId;
        private String address;
        private int port;
        private boolean active;

        public ServerInfo(String serverId, String address, int port, boolean active) {
            this.serverId = serverId;
            this.address = address;
            this.port = port;
            this.active = active;
        }

        public String getServerId() {
            return serverId;
        }

        public String getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        @Override
        public String toString() {
            return "ServerInfo{" +
                    "serverId='" + serverId + '\'' +
                    ", address='" + address + '\'' +
                    ", port=" + port +
                    ", active=" + active +
                    '}';
        }
    }
}