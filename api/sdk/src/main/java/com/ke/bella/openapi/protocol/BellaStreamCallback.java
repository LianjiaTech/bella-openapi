package com.ke.bella.openapi.protocol;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.ke.bella.openapi.common.exception.ChannelException;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

@Slf4j
public class BellaStreamCallback implements Callback {
    @Setter
    protected CompletableFuture<?> connectionInitFuture;

    private final Callbacks.HttpStreamTtsCallback callback;

    public BellaStreamCallback(Callbacks.HttpStreamTtsCallback callback) {
        this.callback = callback;
    }

    public void onOpen() {
        this.connectionInitFuture.complete(null);
        callback.onOpen();
    }

    @Override
    public void onFailure(Call call, IOException e) {
        log.error("流式请求失败", e);
        ChannelException exception = ChannelException.fromException(e);
        if(!connectionInitFuture.isDone()) {
            connectionInitFuture.completeExceptionally(exception);
        } else {
            callback.finish(exception);
        }
    }

    @Override
    public void onResponse(Call call, Response response) {
        if(!response.isSuccessful()) {
            String errorMsg = "流式请求返回错误状态码: " + response.code() + ", message: " + response.message();
            log.error(errorMsg);
            ChannelException exception = ChannelException.fromResponse(response.code(), response.message());
            if(connectionInitFuture.isDone()) {
                callback.finish(exception);
            } else {
                connectionInitFuture.completeExceptionally(exception);
            }
            return;
        }

        onOpen();

        ResponseBody body = response.body();
        if(body == null) {
            log.warn("流式响应体为空");
            callback.finish();
            return;
        }
        try {
            byte[] buffer = new byte[8192];
            try (BufferedSource source = body.source()) {
                int bytesRead;
                while ((bytesRead = source.read(buffer)) != -1) {
                    if (bytesRead > 0) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        callback.callback(data);
                    }
                }
                callback.finish();
            }
        } catch (IOException e) {
            log.error("读取流式数据失败", e);
            ChannelException exception = ChannelException.fromException(e);
            callback.finish(exception);
        } finally {
            if (body != null) {
                body.close();
            }
        }
    }
}
