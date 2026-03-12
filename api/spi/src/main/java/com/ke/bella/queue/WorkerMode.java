package com.ke.bella.queue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WorkerMode {

    NONE(0),
    SINGLE(1),
    BATCH(2);

    private final Integer code;

    public static WorkerMode of(Byte code) {
        if(code == null) {
            return NONE;
        }
        for (WorkerMode mode : values()) {
            if(mode.code == code.intValue()) {
                return mode;
            }
        }
        return NONE;
    }

    public boolean supportsSingle() {
        return (this.code & 1) == 1;
    }

    public boolean supportsBatch() {
        return (this.code & 2) == 2;
    }

    public boolean supports(Integer targetMode) {
        if(targetMode == null) {
            return false;
        }
        return getCode().equals(targetMode) || (getCode() & targetMode) != 0;
    }

    public static boolean isValid(Integer code) {
        for (WorkerMode mode : values()) {
            if(mode.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

}
