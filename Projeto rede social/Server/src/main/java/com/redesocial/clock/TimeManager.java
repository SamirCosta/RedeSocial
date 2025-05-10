package com.redesocial.clock;

import com.redesocial.model.ServerState;
import com.redesocial.util.EventLogger;

/**
 * Gerenciador de tempo que mantém o relógio lógico e o relógio físico ajustado
 * para sincronização entre servidores.
 */
public class TimeManager {
    private static TimeManager instance;
    private final ServerState serverState;
    private final EventLogger logger;

    // Relógio lógico (contador de Lamport)
    private long logicalClock;

    // Representa o offset do relógio físico em relação ao tempo coordenado
    private long clockOffset;

    /**
     * Construtor privado para padrão Singleton
     */
    private TimeManager(ServerState serverState, EventLogger logger) {
        this.serverState = serverState;
        this.logger = logger;
        this.logicalClock = 0;
        this.clockOffset = serverState.getClockOffset();
        this.logger.log("TimeManager inicializado com offset: " + clockOffset);
    }

    /**
     * Obtém a instância do TimeManager (Singleton)
     */
    public static synchronized TimeManager initialize(ServerState serverState, EventLogger logger) {
        if (instance == null) {
            instance = new TimeManager(serverState, logger);
        }
        return instance;
    }

    /**
     * Obtém a instância do TimeManager
     * @return A instância do TimeManager
     * @throws IllegalStateException se o TimeManager não foi inicializado
     */
    public static TimeManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("TimeManager não foi inicializado");
        }
        return instance;
    }

    /**
     * Incrementa o relógio lógico (para eventos locais)
     * @return O novo valor do relógio lógico
     */
    public synchronized long incrementLogicalClock() {
        return ++logicalClock;
    }

    /**
     * Atualiza o relógio lógico com base no timestamp recebido de outro processo
     * @param receivedLogicalTime O relógio lógico recebido de outro processo
     * @return O novo valor do relógio lógico
     */
    public synchronized long updateLogicalClock(long receivedLogicalTime) {
        logicalClock = Math.max(logicalClock, receivedLogicalTime) + 1;
        return logicalClock;
    }

    /**
     * Obtém o tempo atual do relógio lógico
     * @return O valor atual do relógio lógico
     */
    public synchronized long getLogicalTime() {
        return logicalClock;
    }

    /**
     * Obtém o tempo físico atual do sistema
     * @return O tempo físico do sistema em milissegundos
     */
    public long getPhysicalTime() {
        return System.currentTimeMillis();
    }

    /**
     * Obtém o tempo físico ajustado, considerando o offset
     * @return O tempo físico ajustado em milissegundos
     */
    public long getAdjustedPhysicalTime() {
        return getPhysicalTime() + clockOffset;
    }

    /**
     * Atualiza o offset do relógio com base no algoritmo de Berkeley
     * @param newOffset O novo offset a ser aplicado
     */
    public synchronized void updateClockOffset(long newOffset) {
        long oldOffset = this.clockOffset;
        this.clockOffset = newOffset;

        // Atualiza o offset no ServerState para persistência
        serverState.setClockOffset(newOffset);

        logger.log("Relógio atualizado: offset anterior=" + oldOffset +
                ", novo offset=" + newOffset +
                ", ajuste=" + (newOffset - oldOffset) + "ms");
    }

    /**
     * Obtém o offset atual do relógio
     * @return O offset atual do relógio em milissegundos
     */
    public synchronized long getClockOffset() {
        return clockOffset;
    }
}