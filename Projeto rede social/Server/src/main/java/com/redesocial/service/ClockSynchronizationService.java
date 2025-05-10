package com.redesocial.service;

import com.redesocial.model.ServerState;
import com.redesocial.clock.ServerCommunication;
import com.redesocial.util.EventLogger;
import com.redesocial.clock.TimeManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serviço de sincronização de relógios que gerencia as comunicações
 * entre servidores para manter os relógios sincronizados.
 */
public class ClockSynchronizationService {
    private final ServerState serverState;
    private final EventLogger logger;
    private final ServerCommunication communication;
    private final AtomicBoolean running;

    // Parâmetros de configuração
    private final int syncPort;
    private final long syncIntervalMs;
    private final long coordinatorCheckIntervalMs;

    /**
     * Construtor para o serviço de sincronização de relógios
     *
     * @param serverState Estado do servidor
     * @param logger Logger de eventos
     * @param syncPort Porta para comunicação de sincronização
     * @param syncIntervalMs Intervalo entre sincronizações em ms
     * @param coordinatorCheckIntervalMs Intervalo para verificação de coordenador em ms
     */
    public ClockSynchronizationService(
            ServerState serverState,
            EventLogger logger,
            int syncPort,
            long syncIntervalMs,
            long coordinatorCheckIntervalMs) {
        this.serverState = serverState;
        this.logger = logger;
        this.syncPort = syncPort;
        this.syncIntervalMs = syncIntervalMs;
        this.coordinatorCheckIntervalMs = coordinatorCheckIntervalMs;
        this.running = new AtomicBoolean(false);

        // Inicializa a comunicação entre servidores
        this.communication = new ServerCommunication(serverState, logger, syncPort);
    }

    /**
     * Inicia o serviço de sincronização de relógios
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.log("Iniciando serviço de sincronização de relógios");

            // Inicializa o gerenciador de tempo
            TimeManager.initialize(serverState, logger);

            // Inicializa os componentes de sincronização
            communication.initializeSynchronization(syncIntervalMs, coordinatorCheckIntervalMs);

            // Inicia a comunicação entre servidores
            communication.start();

            logger.log("Serviço de sincronização de relógios iniciado");
        }
    }

    /**
     * Para o serviço de sincronização de relógios
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.log("Parando serviço de sincronização de relógios");

            // Para a comunicação entre servidores
            communication.stop();

            logger.log("Serviço de sincronização de relógios parado");
        }
    }

    /**
     * Registra um servidor no sistema distribuído
     *
     * @param serverId ID do servidor a ser registrado
     * @param address Endereço IP do servidor
     * @param port Porta do servidor para comunicação de sincronização
     */
    public void registerServer(String serverId, String address, int port) {
        serverState.addServer(serverId, address, port, true);
        logger.log("Servidor registrado: " + serverId + " em " + address + ":" + port);
    }

    /**
     * Remove um servidor do sistema distribuído
     *
     * @param serverId ID do servidor a ser removido
     */
    public void unregisterServer(String serverId) {
        serverState.removeServer(serverId);
        logger.log("Servidor removido: " + serverId);
    }

    /**
     * Atualiza o status de um servidor
     *
     * @param serverId ID do servidor
     * @param active Status de atividade do servidor
     */
    public void updateServerStatus(String serverId, boolean active) {
        serverState.setServerActive(serverId, active);
        logger.log("Status do servidor atualizado: " + serverId + " = " + (active ? "ativo" : "inativo"));
    }
}