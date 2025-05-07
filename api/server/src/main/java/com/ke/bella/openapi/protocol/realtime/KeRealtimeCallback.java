package com.ke.bella.openapi.protocol.realtime;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ke.bella.openapi.protocol.realtime.RealTimeEventType.TRANSCRIPTION_STARTED;

@Slf4j
public class KeRealtimeCallback implements Callbacks.WebSocketCallback {

    private final Sender sender;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;
    private final String taskId;

    private final CompletableFuture<?> startFlag = new CompletableFuture<>();

    private final long startTime = DateTimeUtils.getCurrentMills();
    private final List<Integer> ttsTtfts;
    private boolean end = false;

    public KeRealtimeCallback(Sender sender, EndpointProcessData processData, EndpointLogger logger, String taskId) {
        this.sender = sender;
        this.processData = processData;
        this.ttsTtfts = new ArrayList<>();
        processData.getMetrics().put("tts_metrics", ttsTtfts);
        this.logger = logger;
        this.taskId = taskId;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        processData.setChannelRequestId(taskId);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        sender.send(bytes.toByteArray());
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {

        RealTimeMessage message = JacksonUtils.deserialize(text, RealTimeMessage.class);
        if(message == null || message.getHeader() == null || message.getHeader().getName() == null) {
            LOGGER.warn("无效的ASR响应消息格式:{}", text);
            return;
        }

        String eventName = message.getHeader().getName();
        RealTimeEventType eventType = RealTimeEventType.fromString(eventName);

        if(eventType != TRANSCRIPTION_STARTED) {
            sender.send(text);
        }

        switch (eventType) {
        case TRANSCRIPTION_STARTED:
            startFlag.complete(null);
            break;

        case TTS_TTFT:
            if(message.getPayload() != null && message.getPayload().getLatency() != null) {
                ttsTtfts.add(message.getPayload().getLatency());
            }
            break;

        case SESSION_CLOSE:
            complete();
            break;

        case TASK_FAILED:
            LOGGER.warn("转录失败: {}",
                    message.getHeader().getStatusMessage() != null ?
                            message.getHeader().getStatusMessage() : "未知原因");
            complete();
            break;

        case UNKNOWN:
            LOGGER.warn("收到未知事件类型: {}", eventName);
            break;
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(1000, "Client closing");
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        LOGGER.info("ASR onClosed: code={}, reason={}", code, reason);
        complete();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        if(t != null){
            onError(ChannelException.fromException(t));
        } else {
            onError(ChannelException.fromResponse(response.code(), response.message()));
        }
    }

    @Override
    public boolean started() {
        try {
            startFlag.get(30, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ChannelException.fromException(e);
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn(e.getMessage(), e);
            throw ChannelException.fromException(e);
        }
    }

    private void onError(ChannelException exception) {
        LOGGER.warn("realtime error: {}", exception.getMessage(), exception);
        sender.onError(exception);
        complete();
    }

    private void complete() {
        if (!end) {
            processData.getMetrics().put("ttlt", DateTimeUtils.getCurrentMills() - startTime);
            sender.close();
            logger.log(processData);
            end = true;
        }
    }
}
