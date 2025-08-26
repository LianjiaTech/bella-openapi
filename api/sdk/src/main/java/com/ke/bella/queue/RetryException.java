package com.ke.bella.queue;

public class RetryException extends RuntimeException {

    public RetryException() {
        super();
    }

    public RetryException(String message) {
        super(message);
    }

    public RetryException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryException(Throwable cause) {
        super(cause);
    }
}
