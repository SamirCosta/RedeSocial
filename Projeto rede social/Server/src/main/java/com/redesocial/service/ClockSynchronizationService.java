package com.redesocial.service;

import com.redesocial.model.ServerState;
import com.redesocial.clock.ServerCommunication;
import com.redesocial.util.EventLogger;
import com.redesocial.clock.TimeManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class ClockSynchronizationService {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final AtomicBoolean running;
    private final long syncIntervalMs;
    private final long coordinatorCheckIntervalMs;

    public ClockSynchronizationService(
            ServerState serverState,
            EventLogger logger,
            ServerCommunication communication,
            long syncIntervalMs,
            long coordinatorCheckIntervalMs) {
        this.serverState = serverState;
        this.logger = logger;
        this.syncIntervalMs = syncIntervalMs;
        this.coordinatorCheckIntervalMs = coordinatorCheckIntervalMs;
        this.running = new AtomicBoolean(false);

        this.communication = communication;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.log("Iniciando serviço de sincronização de relógios");

            TimeManager.initialize(serverState, logger);

            communication.initializeSynchronization(syncIntervalMs, coordinatorCheckIntervalMs);

            communication.start();

            logger.log("Serviço de sincronização de relógios iniciado");
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.log("Parando serviço de sincronização de relógios");

            communication.stop();

            logger.log("Serviço de sincronização de relógios parado");
        }
    }

    public void registerServer(String serverId, String address, int port) {
        serverState.addServer(serverId, address, port, true);
        logger.log("Servidor registrado: " + serverId + " em " + address + ":" + port);
    }

    public void unregisterServer(String serverId) {
        serverState.removeServer(serverId);
        logger.log("Servidor removido: " + serverId);
    }

    public void updateServerStatus(String serverId, boolean active) {
        serverState.setServerActive(serverId, active);
        logger.log("Status do servidor atualizado: " + serverId + " = " + (active ? "ativo" : "inativo"));
    }
}