package com.ke.bella.openapi.protocol.tts;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AliCosyVoiceStreamTtsCallback implements Callbacks.HttpStreamTtsCallback {
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_EVENT_PREFIX = "event:";
    private static final String EVENT_TASK_FINISHED = "task-finished";
    private static final String EVENT_TASK_FAILED = "task-failed";

    private final Callbacks.Sender byteSender;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final long startTime = DateTimeUtils.getCurrentMills();
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
    private final StringBuilder sseDataBuffer = new StringBuilder();

    private boolean first = true;
    private String currentEvent;

    public AliCosyVoiceStreamTtsCallback(Callbacks.Sender byteSender, EndpointProcessData processData, EndpointLogger logger) {
        this.byteSender = byteSender;
        this.processData = processData;
        this.logger = logger;
        if(processData != null) {
            processData.setMetrics(new HashMap<>());
        }
    }

    @Override
    public void onOpen() {
    }

    @Override
    public void callback(byte[] msg) {
        if(finished.get()) {
            return;
        }
        for(byte b : msg) {
            if(finished.get()) {
                return;
            }
            if(b == '\n') {
                processBufferedLine();
            } else {
                lineBuffer.write(b);
            }
        }
    }

    private void processBufferedLine() {
        String line = new String(lineBuffer.toByteArray(), StandardCharsets.UTF_8);
        lineBuffer.reset();
        processLine(line);
    }

    private void processLine(String rawLine) {
        if(finished.get()) {
            return;
        }
        String line = rawLine.trim();
        if(line.isEmpty()) {
            flushSseDataBuffer();
            return;
        }
        if(line.startsWith(SSE_EVENT_PREFIX)) {
            currentEvent = line.substring(SSE_EVENT_PREFIX.length()).trim();
            return;
        }
        if(line.startsWith(SSE_DATA_PREFIX)) {
            appendSseData(line.substring(SSE_DATA_PREFIX.length()).trim());
            return;
        }
        if(sseDataBuffer.length() > 0) {
            return;
        }
        if(line.startsWith("{")) {
            processPayload(null, line);
        }
    }

    private void appendSseData(String data) {
        if("[DONE]".equals(data)) {
            sseDataBuffer.setLength(0);
            currentEvent = null;
            finish();
            return;
        }
        sseDataBuffer.append(data);
    }

    private void flushSseDataBuffer() {
        if(sseDataBuffer.length() == 0) {
            String event = currentEvent;
            currentEvent = null;
            if(EVENT_TASK_FINISHED.equals(event)) {
                finish();
            }
            return;
        }
        String payload = sseDataBuffer.toString();
        sseDataBuffer.setLength(0);
        String event = currentEvent;
        currentEvent = null;
        processPayload(event, payload);
    }

    private void processPayload(String event, String payload) {
        if(finished.get() || "[DONE]".equals(payload) || StringUtils.isBlank(payload)) {
            return;
        }
        try {
            AliCosyVoiceResponse response = JacksonUtils.MAPPER.readValue(payload, AliCosyVoiceResponse.class);
            processResponse(event, response);
        } catch (Exception e) {
            log.error("Failed to parse Ali CosyVoice stream data: {}", payload, e);
            finish(BellaException.fromException(e));
        }
    }

    private void processResponse(String event, AliCosyVoiceResponse response) {
        if(finished.get()) {
            return;
        }
        if(response == null) {
            return;
        }
        if(!response.isSuccess() || EVENT_TASK_FAILED.equals(event)) {
            finish(AliCosyVoiceAdaptor.toChannelException(response, HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.getReasonPhrase()));
            return;
        }
        AliCosyVoiceResponse.Output output = response.getOutput();
        if(output != null && output.getAudio() != null && StringUtils.isNotBlank(output.getAudio().getData())) {
            try {
                byteSender.send(Base64.getDecoder().decode(output.getAudio().getData()));
                if(first) {
                    recordMetric("ttft", DateTimeUtils.getCurrentMills() - startTime);
                    first = false;
                }
            } catch (IllegalArgumentException e) {
                finish(new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                        HttpStatus.BAD_GATEWAY.getReasonPhrase(), "Invalid base64 audio in Ali CosyVoice stream response"));
                return;
            }
        }
        if(EVENT_TASK_FINISHED.equals(event)) {
            finish();
        }
    }

    protected void flushLineBuffer() {
        if(lineBuffer.size() > 0) {
            processBufferedLine();
        }
        flushSseDataBuffer();
    }

    @Override
    public void finish() {
        if(finished.get()) {
            return;
        }
        flushLineBuffer();
        complete();
    }

    @Override
    public void finish(BellaException exception) {
        if(finished.get()) {
            return;
        }
        if(processData != null) {
            processData.setResponse(OpenapiResponse.errorResponse(exception.convertToOpenapiError()));
        }
        complete();
    }

    private void complete() {
        if(!finished.compareAndSet(false, true)) {
            return;
        }
        recordMetric("ttlt", DateTimeUtils.getCurrentMills() - startTime);
        byteSender.close();
        if(logger != null && processData != null) {
            logger.log(processData);
        }
    }

    private void recordMetric(String key, long value) {
        if(processData != null && processData.getMetrics() != null) {
            processData.getMetrics().put(key, value);
        }
    }
}
