package com.ke.bella.openapi.protocol;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.AsyncContext;
import java.io.IOException;
import java.io.OutputStream;

@Slf4j
public class StreamByteSender implements Callbacks.Sender {

    private final AsyncContext context;
    private final OutputStream stream;
    private final boolean async;

    public StreamByteSender(AsyncContext context, OutputStream stream, boolean async) {
        this.context = context;
        this.stream = stream;
        this.async = async;
    }

    public StreamByteSender(AsyncContext context, OutputStream stream) {
        this.context = context;
        this.stream = stream;
        this.async = true;
    }

    @Override
    public void send(String text) {

    }

    @Override
    public void send(byte[] bytes) {
        try {
            stream.write(bytes);
            if(async) {
                stream.flush();
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    @Override
    public void onError(Throwable e) {
    }

    @Override
    public void close() {
        if(!async) {
            return;
        }
        try {
            stream.flush();
            stream.close();
            context.complete();
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }
}
