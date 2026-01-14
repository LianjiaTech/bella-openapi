package com.ke.bella.openapi.mock;

import lombok.AllArgsConstructor;

import java.util.List;
import java.util.concurrent.ExecutorService;

@AllArgsConstructor
public class MockNetworkIO {

    private ExecutorService executorService;

    public <T> T httpRequest(T response, int ttlt) {
        try {
            Thread.sleep(ttlt);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
        return response;
    }

    public void sseRequest(MockSseWriter writer, List<?> chunks, int ttft, int interval) {
        Runnable runnable = () -> {
            try {
                writer.onOpen();
                try {
                    Thread.sleep(ttft);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < chunks.size(); i++) {
                    writer.onWrite(chunks.get(i));
                    if(i != chunks.size() - 1) {
                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } catch (Exception e) {
                writer.onError(e);
            } finally {
                writer.onCompletion();
            }
        };
        executorService.submit(runnable);
    }
}
