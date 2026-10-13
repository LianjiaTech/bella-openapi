package com.ke.bella.queue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum QueueMode {

    NONE(0),
    SINGLE(1),
    ROUTE(2),
    SINGLE_ROUTE(3),
    BATCH(4),
    BATCH_ROUTE(6);

    private static final int SINGLE_BIT = 1;
    private static final int ROUTE_BIT = 2;
    private static final int BATCH_BIT = 4;
    private static final int SUPPORTED_MASK = SINGLE_BIT | ROUTE_BIT | BATCH_BIT;

    private final Integer code;

    public static QueueMode of(Byte code) {
        if(code == null) {
            return NONE;
        }
        for (QueueMode mode : values()) {
            if(mode.code == code.intValue()) {
                return mode;
            }
        }
        return NONE;
    }

    public boolean supportsSingle() {
        return (this.code & SINGLE_BIT) == SINGLE_BIT;
    }

    public boolean supportsRoute() {
        return (this.code & ROUTE_BIT) == ROUTE_BIT;
    }

    public boolean supportsBatch() {
        return (this.code & BATCH_BIT) == BATCH_BIT;
    }

    public QueueMode toWorkerMode() {
        if(supportsBatch()) {
            return BATCH;
        }
        if(supportsSingle()) {
            return SINGLE;
        }
        return NONE;
    }

    public QueueMode toWorkerModeOrThrow() {
        QueueMode workerMode = toWorkerMode();
        if(workerMode != NONE) {
            return workerMode;
        }
        throw new IllegalStateException("queueMode does not map to worker mode: " + code);
    }

    public boolean supports(Integer targetMode) {
        if(!isValid(targetMode)) {
            return false;
        }
        return (getCode() & targetMode) == targetMode;
    }

    public static boolean isValid(Integer code) {
        if(code == null || code < 0 || (code & ~SUPPORTED_MASK) != 0) {
            return false;
        }
        boolean hasSingle = (code & SINGLE_BIT) == SINGLE_BIT;
        boolean hasBatch = (code & BATCH_BIT) == BATCH_BIT;
        return !(hasSingle && hasBatch);
    }

}
