package com.ke.bella.queue;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum QueueMode {

    NONE(0),
    PULL(1),
    ROUTE(2),
    BOTH(3);

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

    public boolean supportsPull() {
        return (this.code & 1) == 1;
    }

    public boolean supportsRoute() {
        return (this.code & 2) == 2;
    }

    public boolean supports(Integer targetMode) {
        if(targetMode == null) {
            return false;
        }
        return getCode().equals(targetMode) || (getCode() & targetMode) != 0;
    }

    public static boolean isValid(Integer code) {
        for (QueueMode mode : values()) {
            if(mode.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

}
