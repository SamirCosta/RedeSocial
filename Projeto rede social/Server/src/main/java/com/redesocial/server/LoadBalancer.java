package com.redesocial.server;

import com.redesocial.util.EventLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {
    private final List<ServerInfo> servers;
    private final AtomicInteger currentIndex;
    private final EventLogger logger;

    public LoadBalancer(EventLogger logger) {
        this.servers = new ArrayList<>();
        this.currentIndex = new AtomicInteger(0);
        this.logger = logger;
    }

    // Adiciona um servidor à lista de servidores disponíveis
    public synchronized void addServer(String serverId, String address, int port) {
        // Verifica se o servidor já existe
        for (ServerInfo server : servers) {
            if (server.getServerId().equals(serverId)) {
                // Atualizar informações se o servidor já existir
                server.setActive(true);
                logger.log("Servidor atualizado no balanceador: " + server);
                return;
            }
        }

        // Adiciona novo servidor se não existir
        ServerInfo serverInfo = new ServerInfo(serverId, address, port, true);
        servers.add(serverInfo);
        logger.log("Servidor adicionado ao balanceador: " + serverInfo);
    }

    // Remove um servidor da lista
    public synchronized void removeServer(String serverId) {
        servers.removeIf(server -> server.getServerId().equals(serverId));
        logger.log("Servidor removido do balanceador: " + serverId);
    }

    // Atualiza o status de um servidor
    public synchronized void setServerStatus(String serverId, boolean active) {
        for (ServerInfo server : servers) {
            if (server.getServerId().equals(serverId)) {
                server.setActive(active);
                logger.log("Status do servidor " + serverId + " atualizado para: " + (active ? "ativo" : "inativo"));
                break;
            }
        }
    }

    // Obtém o próximo servidor usando Round Robin
    public synchronized ServerInfo getNextServer() {
        if (servers.isEmpty()) {
            logger.log("Nenhum servidor disponível no balanceador");
            return null;
        }

        // Filtra apenas servidores ativos
        List<ServerInfo> activeServers = new ArrayList<>();
        for (ServerInfo server : servers) {
            if (server.isActive()) {
                activeServers.add(server);
            }
        }

        if (activeServers.isEmpty()) {
            logger.log("Nenhum servidor ativo disponível no balanceador");
            return null;
        }

        // Implementação do Round Robin
        int index = currentIndex.getAndIncrement() % activeServers.size();
        ServerInfo selectedServer = activeServers.get(index);
        logger.log("Servidor selecionado pelo balanceador: " + selectedServer);

        return selectedServer;
    }

    // Classe interna para armazenar informações do servidor
    public static class ServerInfo {
        private final String serverId;
        private final String address;
        private final int port;
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

    // Método para depuração - lista todos os servidores
    public synchronized List<ServerInfo> getAllServers() {
        return new ArrayList<>(servers);
    }
}