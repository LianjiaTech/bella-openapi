package com.ke.bella.job.queue.api.enums;

import lombok.Getter;

@Getter
public enum ResponseModeEnum {
    CALLBACK("callback"),
    BLOCKING("blocking"),
    STREAM("stream");

    private final String mode;

    ResponseModeEnum(String mode) {
        this.mode = mode;
    }

    public String getModeString() {
        return this.mode;
    }
}
