package com.ke.bella.openapi.mock;

public interface MockSseWriter {
    void onOpen();

    void onWrite(Object chunk);

    void onCompletion();

    void onError(Throwable error);
}
