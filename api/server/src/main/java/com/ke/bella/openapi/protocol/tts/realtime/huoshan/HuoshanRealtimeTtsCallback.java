package com.ke.bella.openapi.protocol.tts.realtime.huoshan;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsConnectionAwareCallback;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsPayload;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsTaskContext;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsTaskListener;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsUpstreamConnection;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class HuoshanRealtimeTtsCallback implements Callbacks.WebSocketCallback, RealtimeTtsConnectionAwareCallback {
    private final HuoshanTtsWsCodec codec;
    private final RealtimeTtsTaskListener listener;
    @Getter
    private final RealtimeTtsTaskContext taskContext;
    private volatile RealtimeTtsUpstreamConnection connection;
    private volatile WebSocket webSocket;
    private volatile boolean connectionStarted;
    private volatile boolean cancelRequested;

    public HuoshanRealtimeTtsCallback(HuoshanTtsWsCodec codec, RealtimeTtsTaskListener listener, RealtimeTtsTaskContext taskContext) {
        this.codec = codec;
        this.listener = listener;
        this.taskContext = taskContext;
    }

    @Override
    public void setConnection(RealtimeTtsUpstreamConnection connection) {
        this.connection = connection;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        this.webSocket = webSocket;
        RealtimeTtsUpstreamConnection current = connection;
        if(current != null) {
            current.setWebSocket(webSocket);
            RealtimeTtsTaskContext active = current.activeTask();
            if(active != null) {
                active.getMetrics().put("upstream_ws_open_ms", DateTimeUtils.getCurrentMills() - active.getUpstreamConnectStartMillis());
            }
        }
        webSocket.send(codec.startConnectionFrame());
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        HuoshanTtsWsCodec.HuoshanTtsWsEvent event = codec.parse(bytes.toByteArray());
        if(event.isError()) {
            if(event.getEvent() == HuoshanTtsWsCodec.EVENT_SESSION_FAILED) {
                failSession(event);
                return;
            }
            if(event.getEvent() == HuoshanTtsWsCodec.EVENT_CONNECTION_FAILED) {
                markUnhealthy();
                failActive("provider_error", event.getProviderMessage(), event.getProviderCode(), event.getProviderMessage());
                return;
            }
            failActive("provider_error", event.getProviderMessage(), event.getProviderCode(), event.getProviderMessage());
            return;
        }
        switch (event.getEvent()) {
        case HuoshanTtsWsCodec.EVENT_CONNECTION_STARTED:
            connectionStarted = true;
            RealtimeTtsUpstreamConnection current = connection;
            RealtimeTtsTaskContext active = activeTask();
            if(current != null) {
                current.setConnectionStarted(true);
                current.setConnectionStartedAtMillis(DateTimeUtils.getCurrentMills());
            }
            if(active != null) {
                active.getMetrics().put("connection_started_ms", DateTimeUtils.getCurrentMills() - active.getUpstreamConnectStartMillis());
                sendStartSession(webSocket, active);
            }
            break;
        case HuoshanTtsWsCodec.EVENT_SESSION_STARTED:
            RealtimeTtsTaskContext startedTask = sessionTask(event);
            if(startedTask != null) {
                Long sentAt = startedTask.getUpstreamSessionStartSentMillis();
                if(sentAt != null) {
                    startedTask.getMetrics().put("session_started_ms", DateTimeUtils.getCurrentMills() - sentAt);
                }
                listener.onSpeechStarted(startedTask);
            }
            break;
        case HuoshanTtsWsCodec.EVENT_TTS_RESPONSE:
            RealtimeTtsTaskContext audioTask = sessionTask(event);
            if(audioTask != null && event.getPayload() != null && event.getPayload().length > 0) {
                listener.onAudio(audioTask, event.getPayload(), durationMs(audioTask, event.getPayload()));
            }
            break;
        case HuoshanTtsWsCodec.EVENT_TTS_SUBTITLE:
            handleSubtitle(event, sessionTask(event));
            break;
        case HuoshanTtsWsCodec.EVENT_SESSION_FINISHED:
            RealtimeTtsTaskContext finishedTask = sessionTask(event);
            if(finishedTask != null) {
                listener.onCompleted(finishedTask, usage(event.getMetaJson()));
            }
            break;
        case HuoshanTtsWsCodec.EVENT_SESSION_CANCELED:
            RealtimeTtsTaskContext cancelledTask = sessionTask(event);
            if(cancelledTask != null) {
                listener.onCancelled(cancelledTask, "provider_cancelled");
            }
            break;
        case HuoshanTtsWsCodec.EVENT_SESSION_FAILED:
            failSession(event);
            break;
        case HuoshanTtsWsCodec.EVENT_CONNECTION_FAILED:
            markUnhealthy();
            failActive("provider_error", event.getProviderMessage(), event.getProviderCode(), event.getProviderMessage());
            break;
        default:
            break;
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        log.debug("huoshan realtime tts text message: {}", text);
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        markUnhealthy();
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        markUnhealthy();
        RealtimeTtsTaskContext active = activeTask();
        if(active == null || active.getState() == RealtimeTtsTaskContext.State.COMPLETED) {
            return;
        }
        if(cancelRequested) {
            listener.onCancelled(active, StringUtils.defaultIfBlank(reason, "provider_cancelled"));
        } else {
            listener.onFailed(active, "provider_connection_closed", StringUtils.defaultIfBlank(reason, "provider websocket closed"),
                    String.valueOf(code), reason);
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        markUnhealthy();
        String message = t != null ? t.getMessage() : response == null ? "provider websocket failed" : response.message();
        RealtimeTtsTaskContext active = activeTask();
        if(active != null && active.getState() != RealtimeTtsTaskContext.State.COMPLETED) {
            listener.onFailed(active, "provider_failure", message, response == null ? null : String.valueOf(response.code()), message);
        }
    }

    @Override
    public boolean started() {
        return connectionStarted;
    }

    void markCancelRequested() {
        this.cancelRequested = true;
    }

    void markSessionStartSent(RealtimeTtsTaskContext taskContext) {
        cancelRequested = false;
        taskContext.setUpstreamSessionStartSentMillis(DateTimeUtils.getCurrentMills());
    }

    private void sendStartSession(WebSocket webSocket, RealtimeTtsTaskContext taskContext) {
        markSessionStartSent(taskContext);
        boolean sent = webSocket.send(codec.startSessionFrame(taskContext.getProviderSessionId(), taskContext.getFrozenPayload()));
        if(!sent) {
            markUnhealthy();
            listener.onFailed(taskContext, "start_session_send_failed", "Failed to start realtime TTS provider session", null, null);
        }
    }

    private RealtimeTtsTaskContext sessionTask(HuoshanTtsWsCodec.HuoshanTtsWsEvent event) {
        RealtimeTtsUpstreamConnection current = connection;
        if(current != null) {
            RealtimeTtsTaskContext resolved = current.getSession(event.getSessionId());
            if(resolved == null) {
                log.debug("ignore huoshan realtime tts event {} for unknown session {}", event.getEvent(), event.getSessionId());
            }
            return resolved;
        }
        return taskContext;
    }

    private RealtimeTtsTaskContext activeTask() {
        RealtimeTtsUpstreamConnection current = connection;
        return current == null ? taskContext : current.activeTask();
    }

    private void failActive(String code, String message, String providerCode, String providerMessage) {
        RealtimeTtsTaskContext active = activeTask();
        if(active != null && active.getState() != RealtimeTtsTaskContext.State.COMPLETED) {
            listener.onFailed(active, code, message, providerCode, providerMessage);
        }
    }

    private void failSession(HuoshanTtsWsCodec.HuoshanTtsWsEvent event) {
        RealtimeTtsTaskContext failedTask = sessionTask(event);
        if(failedTask != null) {
            failedTask.setProviderSessionFailure(true);
            listener.onFailed(failedTask, "provider_error", event.getProviderMessage(), event.getProviderCode(), event.getProviderMessage());
        }
    }

    private void markUnhealthy() {
        RealtimeTtsUpstreamConnection current = connection;
        if(current != null) {
            current.setHealthy(false);
        }
    }

    private Long durationMs(RealtimeTtsTaskContext taskContext, byte[] audio) {
        RealtimeTtsPayload payload = taskContext.getFrozenPayload();
        if(payload == null || payload.getSampleRate() == null || payload.getChannels() == null) {
            return null;
        }
        String format = payload.getFormat();
        String encoding = payload.getEncoding();
        if(!("pcm".equalsIgnoreCase(format) || "raw".equalsIgnoreCase(format)) || !"s16le".equalsIgnoreCase(encoding)) {
            return null;
        }
        long samples = audio.length / Math.max(1, payload.getChannels()) / 2;
        return samples * 1000 / payload.getSampleRate();
    }

    private void handleSubtitle(HuoshanTtsWsCodec.HuoshanTtsWsEvent event, RealtimeTtsTaskContext taskContext) {
        if(taskContext == null || event.getPayload() == null || event.getPayload().length == 0) {
            return;
        }
        Map<String, Object> payload = JacksonUtils.toMap(new String(event.getPayload(), StandardCharsets.UTF_8));
        if(payload == null) {
            return;
        }
        RealtimeTtsPayload timestamp = RealtimeTtsPayload.builder()
                .type("word")
                .items(timestampItems(payload))
                .providerMetadata(payload)
                .build();
        listener.onTimestamp(taskContext, timestamp);
    }

    private List<RealtimeTtsPayload.TimestampItem> timestampItems(Map<String, Object> payload) {
        List<RealtimeTtsPayload.TimestampItem> items = new ArrayList<>();
        appendTimestampItems(items, payload.get("words"));
        appendTimestampItems(items, payload.get("subtitles"));
        Object result = payload.get("result");
        if(result instanceof Map) {
            appendTimestampItems(items, ((Map<?, ?>) result).get("subtitles"));
        }
        return items.isEmpty() ? null : items;
    }

    private void appendTimestampItems(List<RealtimeTtsPayload.TimestampItem> items, Object source) {
        if(!(source instanceof List)) {
            return;
        }
        for (Object value : (List<?>) source) {
            if(!(value instanceof Map)) {
                continue;
            }
            Map<?, ?> item = (Map<?, ?>) value;
            items.add(RealtimeTtsPayload.TimestampItem.builder()
                    .text(stringValue(first(item, "word", "text", "Text")))
                    .startMs(timestampStartMs(item))
                    .endMs(timestampEndMs(item))
                    .beginIndex(intValue(first(item, "begin_index", "beginIndex", "BeginIndex")))
                    .endIndex(intValue(first(item, "end_index", "endIndex", "EndIndex")))
                    .phoneme(stringValue(first(item, "phoneme", "Phoneme")))
                    .build());
        }
    }

    private Long timestampStartMs(Map<?, ?> item) {
        Object millis = first(item, "start_ms", "startMs", "BeginTime", "beginTime");
        if(millis != null) {
            return longValue(millis);
        }
        return secondsToMillis(first(item, "startTime", "start_time", "start"));
    }

    private Long timestampEndMs(Map<?, ?> item) {
        Object millis = first(item, "end_ms", "endMs", "EndTime");
        if(millis != null) {
            return longValue(millis);
        }
        return secondsToMillis(first(item, "endTime", "end_time", "end"));
    }

    private Object first(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if(value != null) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        Long longValue = longValue(value);
        return longValue == null ? null : longValue.intValue();
    }

    private Long longValue(Object value) {
        if(value instanceof Number) {
            return ((Number) value).longValue();
        }
        if(value instanceof String && StringUtils.isNotBlank((String) value)) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long secondsToMillis(Object value) {
        if(value instanceof Number) {
            return Math.round(((Number) value).doubleValue() * 1000);
        }
        if(value instanceof String && StringUtils.isNotBlank((String) value)) {
            try {
                return Math.round(Double.parseDouble((String) value) * 1000);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> usage(String metaJson) {
        Map<String, Object> meta = JacksonUtils.toMap(metaJson);
        if(meta == null) {
            return null;
        }
        Object usage = meta.get("usage");
        if(usage instanceof Map) {
            return (Map<String, Object>) usage;
        }
        return meta;
    }
}
