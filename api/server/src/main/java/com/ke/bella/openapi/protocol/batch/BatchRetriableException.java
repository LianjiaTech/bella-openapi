package com.ke.bella.openapi.protocol.batch;

public class BatchRetriableException extends RuntimeException {

    public BatchRetriableException(String message) {
        super(message);
    }

    public BatchRetriableException(String message, Throwable cause) {
        super(message, cause);
    }
}
