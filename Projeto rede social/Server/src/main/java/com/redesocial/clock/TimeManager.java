package com.redesocial.clock;

import com.redesocial.model.ServerState;
import com.redesocial.util.EventLogger;

public class TimeManager {
    private static TimeManager instance;
    private final ServerState serverState;
    private final EventLogger logger;

    private long logicalClock;

    private long clockOffset;

    private TimeManager(ServerState serverState, EventLogger logger) {
        this.serverState = serverState;
        this.logger = logger;
        this.logicalClock = 0;
        this.clockOffset = serverState.getClockOffset();
        this.logger.log("TimeManager inicializado com offset: " + clockOffset);
    }

    public static synchronized TimeManager initialize(ServerState serverState, EventLogger logger) {
        if (instance == null) {
            instance = new TimeManager(serverState, logger);
        }
        return instance;
    }

    public static TimeManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("TimeManager não foi inicializado");
        }
        return instance;
    }

    public synchronized long incrementLogicalClock() {
        return ++logicalClock;
    }

    public synchronized long updateLogicalClock(long receivedLogicalTime) {
        logicalClock = Math.max(logicalClock, receivedLogicalTime) + 1;
        return logicalClock;
    }

    public synchronized long getLogicalTime() {
        return logicalClock;
    }

    public long getPhysicalTime() {
        return System.currentTimeMillis();
    }

    public long getAdjustedPhysicalTime() {
        return getPhysicalTime() + clockOffset;
    }

    public synchronized void updateClockOffset(long newOffset) {
        long oldOffset = this.clockOffset;
        this.clockOffset = newOffset;

        serverState.setClockOffset(newOffset);

        logger.log("Relógio atualizado: offset anterior=" + oldOffset +
                ", novo offset=" + newOffset +
                ", ajuste=" + (newOffset - oldOffset) + "ms");
    }

    public synchronized long getClockOffset() {
        return clockOffset;
    }
}