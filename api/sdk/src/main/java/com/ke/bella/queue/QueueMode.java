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

    public static QueueMode fromCode(Integer code) {
        if(code == null) {
            return NONE;
        }
        for (QueueMode mode : values()) {
            if(mode.code.equals(code)) {
                return mode;
            }
        }
        return NONE;
    }

    public static boolean supportsPull(Integer code) {
        if(code == null) {
            return false;
        }
        return (code & 1) == 1;
    }

    public static boolean supportsRoute(Integer code) {
        if(code == null) {
            return false;
        }
        return (code & 2) == 2;
    }

    public boolean supportsPull() {
        return supportsPull(this.code);
    }

    public boolean supportsRoute() {
        return supportsRoute(this.code);
    }

    public static boolean supports(Byte mode, Integer targetMode) {
        if(mode == null || targetMode == null) {
            return false;
        }
        return (mode & targetMode) != 0;
    }

}
