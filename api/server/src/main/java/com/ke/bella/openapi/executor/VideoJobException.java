package com.ke.bella.openapi.executor;

import lombok.Getter;

@Getter
public class VideoJobException extends RuntimeException {

    private final String code;
    private final String message;

    public VideoJobException(Code code, String message) {
        super(message);
        this.code = code.getCode();
        this.message = message;
    }

    @Getter
    public enum Code {
        JOB_NOT_FOUND("job_not_found"),
        INVALID_STATUS("invalid_status"),
        CHANNEL_NOT_FOUND("channel_not_found"),
        ADAPTOR_NOT_FOUND("adaptor_not_found"),
        TRANSFER_FAILED("transfer_failed"),
        STATE_UPDATE_FAILED("state_update_failed");

        private final String code;

        Code(String code) {
            this.code = code;
        }
    }
}
