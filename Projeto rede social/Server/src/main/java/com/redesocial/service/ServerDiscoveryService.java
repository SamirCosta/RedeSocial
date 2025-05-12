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

/**
 * Serviço para descoberta e monitoramento de servidores na rede
 */
public class ServerDiscoveryService {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final ScheduledExecutorService scheduler;
    private final long discoveryIntervalMs;

    /**
     * Construtor para o serviço de descoberta de servidores
     *
     * @param serverState Estado do servidor
     * @param logger Logger de eventos
     * @param communication Comunicação entre servidores
     * @param discoveryIntervalMs Intervalo entre as verificações de servidores
     */
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

    /**
     * Inicia o serviço de descoberta e monitoramento
     */
    public void start() {
        logger.log("Iniciando serviço de descoberta de servidores");

        // Agenda verificação periódica dos servidores
        scheduler.scheduleAtFixedRate(this::checkServers,
                5000, // iniciar após 5 segundos para dar tempo para inicialização completa
                discoveryIntervalMs,
                TimeUnit.MILLISECONDS);

        // Registra este servidor na rede enviando anúncio
        // Agenda para iniciar após 8 segundos para dar tempo para todos os serviços iniciarem
        scheduler.schedule(this::announcePresence, 8000, TimeUnit.MILLISECONDS);
    }

    /**
     * Para o serviço de descoberta
     */
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

    /**
     * Anuncia a presença deste servidor na rede
     */
    private void announcePresence() {
        logger.log("Anunciando presença do servidor " + serverState.getServerId() + " na rede");

        JSONObject announcement = new JSONObject();
        announcement.put("action", "SERVER_ANNOUNCEMENT");
        announcement.put("serverId", serverState.getServerId());
        announcement.put("serverAddress", serverState.getServerAddress());
        announcement.put("serverPort", serverState.getServerPort());
        announcement.put("syncAddress", communication.getSyncBindAddress());

        // Adicionar também a porta do serviço para o balanceador
        announcement.put("servicePort", serverState.getServerPort());

        // Envia anúncio para todos os servidores conhecidos
        for (Map.Entry<String, ServerState.ServerInfo> entry : serverState.getKnownServers().entrySet()) {
            String serverId = entry.getKey();
            if (!serverId.equals(serverState.getServerId())) {
                communication.sendMessage(serverId, announcement.toString());
            }
        }
    }

    /**
     * Verifica o status dos servidores conhecidos
     */
    private void checkServers() {
        logger.log("Verificando status dos servidores...");
        int activeServers = 0;
        int inactiveServers = 0;

        // Para cada servidor conhecido
        for (Map.Entry<String, ServerState.ServerInfo> entry : serverState.getKnownServers().entrySet()) {
            String serverId = entry.getKey();
            ServerState.ServerInfo serverInfo = entry.getValue();

            // Não verificamos nosso próprio servidor
            if (serverId.equals(serverState.getServerId())) {
                continue;
            }

            // Envia ping para verificar se o servidor está ativo
            try {
                JSONObject pingMessage = new JSONObject();
                pingMessage.put("action", "SERVER_PING");
                pingMessage.put("fromServer", serverState.getServerId());

                String response = communication.sendMessageWithResponse(serverId, pingMessage.toString());
                JSONObject jsonResponse = new JSONObject(response);

                if (jsonResponse.getBoolean("success")) {
                    // Atualiza o status do servidor como ativo
                    if (!serverInfo.isActive()) {
                        serverState.setServerActive(serverId, true);
                        logger.log("Servidor " + serverId + " está ativo novamente");
                    }
                    activeServers++;
                }
            } catch (Exception e) {
                // Em caso de falha na comunicação, marca o servidor como inativo
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

    /**
     * Processa um anúncio de servidor recebido
     *
     * @param serverId ID do servidor
     * @param serverAddress Endereço do servidor
     * @param serverPort Porta do servidor
     * @param syncAddress Endereço de sincronização completo
     */
    public void processServerAnnouncement(String serverId, String serverAddress,
                                          int serverPort, String syncAddress) {

        logger.log("Recebido anúncio do servidor " + serverId +
                " em " + serverAddress + ":" + serverPort +
                " (sync: " + syncAddress + ")");

        // Extrai a porta de sincronização do endereço completo
        int syncPort = extractPortFromAddress(syncAddress);
        if (syncPort == -1) {
            logger.logError("Erro ao extrair porta de sincronização de " + syncAddress, null);
            return;
        }

        // Adiciona ou atualiza o servidor na lista de conhecidos
        boolean serverExists = serverState.getKnownServers().containsKey(serverId);

        serverState.addServer(serverId, serverAddress, syncPort, true);

        if (!serverExists) {
            logger.log("Novo servidor descoberto: " + serverId);

            // Se este servidor acabou de se registrar, envie um anúncio de volta
            announcePresence();
        } else {
            logger.log("Informações atualizadas para servidor: " + serverId);
        }
    }

    /**
     * Processa um ping de servidor recebido
     *
     * @param fromServer ID do servidor que enviou o ping
     * @return Resposta indicando que este servidor está ativo
     */
    public JSONObject processServerPing(String fromServer) {
        logger.log("Recebido ping do servidor " + fromServer);

        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("serverId", serverState.getServerId());
        response.put("isActive", true);

        return response;
    }

    /**
     * Extrai a porta de um endereço de sincronização
     *
     * @param address Endereço completo (ex: tcp://localhost:6038)
     * @return Número da porta ou -1 em caso de erro
     */
    private int extractPortFromAddress(String address) {
        try {
            // Tentar extrair usando URI
            URI uri = new URI(address);
            if (uri.getPort() != -1) {
                return uri.getPort();
            }

            // Método alternativo: procurar o último ':'
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