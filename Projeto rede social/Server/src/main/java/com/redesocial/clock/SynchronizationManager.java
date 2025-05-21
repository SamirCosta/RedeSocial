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
        int syncPort = Integer.parseInt(config.getProperty("sync.port", "6000"));
        long syncIntervalMs = Long.parseLong(config.getProperty("sync.interval.ms", "60000"));
        long coordCheckIntervalMs = Long.parseLong(config.getProperty("coordinator.check.interval.ms", "30000"));

        this.communication = new ServerCommunication(serverState, logger, syncPort);

        replicationService = new DataReplicationService(serverState, logger, communication);

        communication.setReplicationService(replicationService);

        syncService = new ClockSynchronizationService(
                serverState, logger, communication, syncIntervalMs, coordCheckIntervalMs
        );

        String[] seedServers = config.getSeedServers();
        for (String serverAddress : seedServers) {
            String[] parts = serverAddress.split(":");
            if (parts.length == 3) {
                String serverId = parts[0];
                String address = parts[1];
                int port = Integer.parseInt(parts[2]);

                syncService.registerServer(serverId, address, port);
                logger.log("Servidor registrado: " + serverId + " em " + address + ":" + port);
            }
        }

        logger.log("Serviços de sincronização inicializados");
    }

    public void start() {

        if (syncService != null) {
            syncService.start();
        }

        if (replicationService != null) {
            replicationService.start();
            logger.log("Serviço de replicação iniciado");
        } else {
            logger.logError("ERRO: DataReplicationService é NULL ao tentar iniciar!", null);
        }

        if (communication != null) {
            logger.log("DEBUG: Verificando associação no ServerCommunication...");
        }

        logger.log("Serviços de sincronização iniciados");
    }

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

    public void registerServer(String serverId, String address, int port) {
        if (syncService != null) {
            syncService.registerServer(serverId, address, port);
        }
    }

    public void unregisterServer(String serverId) {
        if (syncService != null) {
            syncService.unregisterServer(serverId);
        }
    }

    public void updateServerStatus(String serverId, boolean active) {
        if (syncService != null) {
            syncService.updateServerStatus(serverId, active);
        }
    }

    public DataReplicationService getReplicationService() {
        if (replicationService == null) {
            logger.logError("AVISO: getReplicationService() retornando NULL!", null);
        }
        return replicationService;
    }
}