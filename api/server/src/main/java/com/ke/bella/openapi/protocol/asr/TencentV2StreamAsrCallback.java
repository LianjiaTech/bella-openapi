package com.ke.bella.openapi.protocol.asr;

import java.io.EOFException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;

@Slf4j
public class TencentV2StreamAsrCallback implements Callbacks.WebSocketCallback {
    @Getter
    private final TencentV2RealTimeAsrRequest request;
    private final Callbacks.Sender sender;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;
    private final Function<TencentV2RealTimeAsrResponse, List<String>> converter;
    private final CompletableFuture<?> startFlag = new CompletableFuture<>();
    private boolean end = false;
    private volatile boolean finalReceived = false;
    private boolean first = true;
    @Getter
    private volatile String speakerContextId;
    private final long startTime = DateTimeUtils.getCurrentMills();

    public TencentV2StreamAsrCallback(TencentV2RealTimeAsrRequest request, Callbacks.Sender sender,
            EndpointProcessData processData, EndpointLogger logger,
            Function<TencentV2RealTimeAsrResponse, List<String>> converter) {
        this.request = request;
        this.sender = sender;
        this.processData = processData;
        this.logger = logger;
        this.converter = converter;
        processData.setMetrics(new HashMap<>());
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        log.info("Tencent ASR V2 WebSocket connection established, voice_id: {}", request.getVoiceId());
        processData.setChannelRequestId(request.getVoiceId());
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        log.warn("Received unexpected Tencent ASR V2 binary message");
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            TencentV2RealTimeAsrResponse response = JacksonUtils.deserialize(text, TencentV2RealTimeAsrResponse.class);
            handleResponse(response);
        } catch (Exception e) {
            log.error("Error parsing Tencent ASR V2 message", e);
            onError(BellaException.fromException(e));
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        log.info("Tencent ASR V2 WebSocket closing: code={}, reason={}", code, reason);
        complete();
        webSocket.close(1000, "Client closing");
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        log.info("Tencent ASR V2 WebSocket closed: code={}, reason={}", code, reason);
        complete();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        if(finalReceived && t instanceof EOFException) {
            return;
        }
        log.error("Tencent ASR V2 WebSocket failure: {}", t.getMessage(), t);
        int httpCode = response != null ? response.code() : 500;
        BellaException exception = new BellaException.ChannelException(httpCode, t.getMessage());
        failStart(exception);
        onError(exception);
    }

    @Override
    public boolean started() {
        try {
            startFlag.get(30, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BellaException.fromException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw BellaException.fromException(e);
        }
    }

    public void sendAudioData(WebSocket webSocket, byte[] audioData) {
        if(audioData != null && audioData.length > 0) {
            webSocket.send(ByteString.of(audioData));
        }
    }

    public void sendEnd(WebSocket webSocket) {
        webSocket.send("{\"type\": \"end\"}");
        log.info("Sent end message to Tencent ASR V2");
    }

    private void handleResponse(TencentV2RealTimeAsrResponse response) {
        if(response == null) {
            return;
        }
        if(!response.isSuccess()) {
            handleTranscriptionFailed(response.getCode(), response.getMessage());
            return;
        }
        if(response.isFinal()) {
            finalReceived = true;
        }
        if(response.getSentences() == null && !response.isFinal()) {
            speakerContextId = response.getSpeakerContextId();
            log.info("Tencent ASR V2 handshake success: {}", response.getMessage());
            startFlag.complete(null);
            return;
        }
        if(response.getSentences() != null) {
            if(first) {
                processData.getMetrics().put("ttft", DateTimeUtils.getCurrentMills() - startTime);
                first = false;
            }
            for (String text : converter.apply(response)) {
                sender.send(text);
            }
            if(response.isFinal()) {
                complete();
            }
        } else if(response.isFinal()) {
            for (String text : converter.apply(response)) {
                sender.send(text);
            }
            complete();
        }
    }

    private void handleTranscriptionFailed(int code, String errorMsg) {
        log.error("Tencent ASR V2 transcription failed: code={}, message={}", code, errorMsg);
        BellaException exception = new BellaException.ChannelException(getHttpCode(code), errorMsg);
        failStart(exception);
        sender.onError(exception);
        complete();
    }

    private int getHttpCode(int code) {
        if(code == 4002 || code == 4003) {
            return 401;
        }
        if(code == 4006) {
            return 429;
        }
        if(code == 6001) {
            return 403;
        }
        if(code == 4008 || code == 4009 || code >= 5000) {
            return 503;
        }
        return 400;
    }

    private void onError(BellaException exception) {
        log.warn("Tencent ASR V2 error: {}", exception.getMessage(), exception);
        failStart(exception);
        sender.onError(exception);
        complete();
    }

    private void failStart(BellaException exception) {
        if(!startFlag.isDone()) {
            startFlag.completeExceptionally(exception);
        }
    }

    private void complete() {
        if(!end) {
            processData.getMetrics().put("ttlt", DateTimeUtils.getCurrentMills() - startTime);
            sender.close();
            if(logger != null) {
                logger.log(processData);
            }
            end = true;
        }
    }
}
