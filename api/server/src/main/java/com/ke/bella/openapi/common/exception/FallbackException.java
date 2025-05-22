package com.ke.bella.openapi.common.exception;

import java.util.List;

/**
 * Exception thrown when all fallback models have been tried but failed
 */
public class FallbackException extends RuntimeException {
    private final List<String> attemptedModels;
    private final List<Exception> exceptions;

    public FallbackException(String message, List<String> attemptedModels, List<Exception> exceptions) {
        super(message);
        this.attemptedModels = attemptedModels;
        this.exceptions = exceptions;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + ". Attempted models: " + String.join(", ", attemptedModels);
    }
}
