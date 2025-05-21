package com.redesocial.service;

import com.redesocial.clock.ServerCommunication;
import com.redesocial.model.ServerState;
import com.redesocial.util.EventLogger;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerDiscoveryService {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final ScheduledExecutorService scheduler;
    private final long discoveryIntervalMs;

    public ServerDiscoveryService(
            ServerState serverState,
            EventLogger logger,
            ServerCommunication communication,
            long discoveryIntervalMs) {
        this.serverState = serverState;
        this.logger = logger;
        this.communication = communication;
        this.discoveryIntervalMs = discoveryIntervalMs;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void start() {
        logger.log("Iniciando serviço de descoberta de servidores");

        scheduler.scheduleAtFixedRate(this::checkServers,
                5000,
                discoveryIntervalMs,
                TimeUnit.MILLISECONDS);

        scheduler.schedule(this::announcePresence, 8000, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.log("Serviço de descoberta de servidores parado");
    }

    private void announcePresence() {
        logger.log("Anunciando presença do servidor " + serverState.getServerId() + " na rede");

        JSONObject announcement = new JSONObject();
        announcement.put("action", "SERVER_ANNOUNCEMENT");
        announcement.put("serverId", serverState.getServerId());
        announcement.put("serverAddress", serverState.getServerAddress());
        announcement.put("serverPort", serverState.getServerPort());
        announcement.put("syncAddress", communication.getSyncBindAddress());

        announcement.put("servicePort", serverState.getServerPort());

        for (Map.Entry<String, ServerState.ServerInfo> entry : serverState.getKnownServers().entrySet()) {
            String serverId = entry.getKey();
            if (!serverId.equals(serverState.getServerId())) {
                communication.sendMessage(serverId, announcement.toString());
            }
        }
    }

    private void checkServers() {
        logger.log("Verificando status dos servidores...");
        int activeServers = 0;
        int inactiveServers = 0;

        for (Map.Entry<String, ServerState.ServerInfo> entry : serverState.getKnownServers().entrySet()) {
            String serverId = entry.getKey();
            ServerState.ServerInfo serverInfo = entry.getValue();

            if (serverId.equals(serverState.getServerId())) {
                continue;
            }

            try {
                JSONObject pingMessage = new JSONObject();
                pingMessage.put("action", "SERVER_PING");
                pingMessage.put("fromServer", serverState.getServerId());

                String response = communication.sendMessageWithResponse(serverId, pingMessage.toString());
                JSONObject jsonResponse = new JSONObject(response);

                if (jsonResponse.getBoolean("success")) {
                    if (!serverInfo.isActive()) {
                        serverState.setServerActive(serverId, true);
                        logger.log("Servidor " + serverId + " está ativo novamente");
                    }
                    activeServers++;
                }
            } catch (Exception e) {
                if (serverInfo.isActive()) {
                    serverState.setServerActive(serverId, false);
                    logger.log("Servidor " + serverId + " está inativo: " + e.getMessage());
                }
                inactiveServers++;
            }
        }

        logger.log("Verificação completa: " + activeServers + " servidores ativos, " +
                inactiveServers + " servidores inativos");
    }

    public void processServerAnnouncement(String serverId, String serverAddress,
                                          int serverPort, String syncAddress) {

        logger.log("Recebido anúncio do servidor " + serverId +
                " em " + serverAddress + ":" + serverPort +
                " (sync: " + syncAddress + ")");

        int syncPort = extractPortFromAddress(syncAddress);
        if (syncPort == -1) {
            logger.logError("Erro ao extrair porta de sincronização de " + syncAddress, null);
            return;
        }

        boolean serverExists = serverState.getKnownServers().containsKey(serverId);

        serverState.addServer(serverId, serverAddress, syncPort, true);

        if (!serverExists) {
            logger.log("Novo servidor descoberto: " + serverId);

            announcePresence();
        } else {
            logger.log("Informações atualizadas para servidor: " + serverId);
        }
    }

    public JSONObject processServerPing(String fromServer) {
        logger.log("Recebido ping do servidor " + fromServer);

        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("serverId", serverState.getServerId());
        response.put("isActive", true);

        return response;
    }

    private int extractPortFromAddress(String address) {
        try {
            URI uri = new URI(address);
            if (uri.getPort() != -1) {
                return uri.getPort();
            }

            int lastColon = address.lastIndexOf(':');
            if (lastColon != -1) {
                String portStr = address.substring(lastColon + 1);
                return Integer.parseInt(portStr);
            }

            return -1;
        } catch (URISyntaxException | NumberFormatException e) {
            logger.logError("Erro ao extrair porta do endereço " + address, e);
            return -1;
        }
    }
}