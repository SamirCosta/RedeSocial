package com.redesocial.clock;

import com.redesocial.model.ServerState;
import com.redesocial.server.ServerConfig;
import com.redesocial.service.ClockSynchronizationService;
import com.redesocial.service.DataReplicationService;
import com.redesocial.util.EventLogger;

public class SynchronizationManager {
    private ServerState serverState;
    private EventLogger logger;
    private ClockSynchronizationService syncService;
    private DataReplicationService replicationService;
    private ServerCommunication communication;

    public SynchronizationManager(ServerState serverState, EventLogger logger) {
        this.serverState = serverState;
        this.logger = logger;
    }


    public void initialize(ServerConfig config) {
        logger.log("Inicializando serviços de sincronização");

        // Obtém parâmetros de configuração
        int syncPort = Integer.parseInt(config.getProperty("sync.port", "6000"));
        long syncIntervalMs = Long.parseLong(config.getProperty("sync.interval.ms", "60000")); // 1 minuto padrão
        long coordCheckIntervalMs = Long.parseLong(config.getProperty("coordinator.check.interval.ms", "30000")); // 30 segundos padrão

        // Inicializa a comunicação entre servidores
        this.communication = new ServerCommunication(serverState, logger, syncPort);

        // Inicializa o serviço de replicação
        replicationService = new DataReplicationService(serverState, logger, communication);

        // Configura o serviço de replicação na comunicação
        communication.setReplicationService(replicationService);

        // Cria o serviço de sincronização de relógios
        syncService = new ClockSynchronizationService(
                serverState, logger, syncPort, syncIntervalMs, coordCheckIntervalMs
        );

        // Registra servidores conhecidos (seed servers)
        String[] seedServers = config.getSeedServers();
        for (String serverAddress : seedServers) {
            String[] parts = serverAddress.split(":");
            if (parts.length == 3) {
                String serverId = parts[0];
                String address = parts[1];
                int port = Integer.parseInt(parts[2]);

                // Registra com a porta de sincronização correta
                syncService.registerServer(serverId, address, port);
                logger.log("Servidor registrado: " + serverId + " em " + address + ":" + port);
            }
        }

        logger.log("Serviços de sincronização inicializados");
    }

    /**
     * Inicia os serviços de sincronização
     */
    public void start() {
        logger.log("Iniciando serviços de sincronização");

        if (syncService != null) {
            syncService.start();
        }

        if (replicationService != null) {
            replicationService.start();
        }

        logger.log("Serviços de sincronização iniciados");
    }

    /**
     * Para os serviços de sincronização
     */
    public void stop() {
        logger.log("Parando serviços de sincronização");

        if (syncService != null) {
            syncService.stop();
        }

        if (replicationService != null) {
            replicationService.stop();
        }

        logger.log("Serviços de sincronização parados");
    }

    /**
     * Registra um novo servidor
     *
     * @param serverId ID do servidor
     * @param address Endereço IP do servidor
     * @param port Porta de sincronização do servidor
     */
    public void registerServer(String serverId, String address, int port) {
        if (syncService != null) {
            syncService.registerServer(serverId, address, port);
        }
    }

    /**
     * Remove um servidor
     *
     * @param serverId ID do servidor a ser removido
     */
    public void unregisterServer(String serverId) {
        if (syncService != null) {
            syncService.unregisterServer(serverId);
        }
    }

    /**
     * Atualiza o status de um servidor
     *
     * @param serverId ID do servidor
     * @param active Status de atividade do servidor
     */
    public void updateServerStatus(String serverId, boolean active) {
        if (syncService != null) {
            syncService.updateServerStatus(serverId, active);
        }
    }

    public DataReplicationService getReplicationService() {
        return replicationService;
    }
}