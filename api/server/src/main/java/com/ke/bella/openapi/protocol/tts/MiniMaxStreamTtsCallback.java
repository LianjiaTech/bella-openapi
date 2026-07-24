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
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class MiniMaxStreamTtsCallback implements Callbacks.HttpStreamTtsCallback {
    private static final String SSE_DATA_PREFIX = "data:";

    private final Callbacks.Sender byteSender;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;

    private boolean first = true;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final long startTime = DateTimeUtils.getCurrentMills();
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
    private final StringBuilder sseDataBuffer = new StringBuilder();

    public MiniMaxStreamTtsCallback(Callbacks.Sender byteSender, EndpointProcessData processData, EndpointLogger logger) {
        this.byteSender = byteSender;
        this.processData = processData;
        this.logger = logger;
        if (processData != null) {
            processData.setMetrics(new HashMap<>());
        }
    }

    @Override
    public void onOpen() {
    }

    @Override
    public void callback(byte[] msg) {
        if (finished.get()) {
            return;
        }
        for (byte b : msg) {
            if (finished.get()) {
                return;
            }
            if (b == '\n') {
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
        if (finished.get()) {
            return;
        }
        String line = rawLine.trim();
        if (line.isEmpty()) {
            flushSseDataBuffer();
            return;
        }
        if (line.startsWith(SSE_DATA_PREFIX)) {
            appendSseData(line.substring(SSE_DATA_PREFIX.length()).trim());
            return;
        }
        if (sseDataBuffer.length() > 0) {
            return;
        }
        if (!line.startsWith("{")) {
            return;
        }
        processPayload(line);
    }

    private void appendSseData(String data) {
        if ("[DONE]".equals(data)) {
            sseDataBuffer.setLength(0);
            return;
        }
        sseDataBuffer.append(data);
        MiniMaxResponse response = tryParseResponse(sseDataBuffer.toString());
        if (response != null) {
            String payload = sseDataBuffer.toString();
            sseDataBuffer.setLength(0);
            processResponse(response, payload);
        }
    }

    private void flushSseDataBuffer() {
        if (sseDataBuffer.length() == 0) {
            return;
        }
        String payload = sseDataBuffer.toString();
        sseDataBuffer.setLength(0);
        processPayload(payload);
    }

    private MiniMaxResponse tryParseResponse(String payload) {
        try {
            return JacksonUtils.MAPPER.readValue(payload, MiniMaxResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void processPayload(String payload) {
        if ("[DONE]".equals(payload) || payload.isEmpty()) {
            return;
        }
        try {
            processResponse(JacksonUtils.MAPPER.readValue(payload, MiniMaxResponse.class), payload);
        } catch (Exception e) {
            log.error("Failed to parse MiniMax stream data: {}", payload, e);
            finish(BellaException.fromException(e));
        }
    }

    private void processResponse(MiniMaxResponse response, String payload) {
        if (finished.get() || response == null) {
            return;
        }
        if (!response.isSuccess()) {
            log.warn("MiniMax stream error: statusCode={}, statusMsg={}",
                    response.getBaseResp() == null ? null : response.getBaseResp().getStatusCode(),
                    response.getBaseResp() == null ? null : response.getBaseResp().getStatusMsg());
            finish(MiniMaxAdaptor.toChannelException(response, HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.getReasonPhrase()));
            return;
        }
        MiniMaxResponse.DataPayload data = response.getData();
        if (data == null) {
            return;
        }
        if (Integer.valueOf(2).equals(data.getStatus())) {
            finish();
            return;
        }
        if (StringUtils.isNotBlank(data.getAudio())) {
            try {
                byteSender.send(MiniMaxAdaptor.decodeHex(data.getAudio()));
                if (first) {
                    recordMetric("ttft", DateTimeUtils.getCurrentMills() - startTime);
                    first = false;
                }
            } catch (BellaException e) {
                log.error("Failed to decode MiniMax stream data: {}", payload, e);
                finish(e);
            }
        }
    }

    protected void flushLineBuffer() {
        if (lineBuffer.size() > 0) {
            processBufferedLine();
        }
        flushSseDataBuffer();
    }

    @Override
    public void finish() {
        if (finished.get()) {
            return;
        }
        flushLineBuffer();
        complete();
    }

    @Override
    public void finish(BellaException exception) {
        if (finished.get()) {
            return;
        }
        if (processData != null) {
            processData.setResponse(OpenapiResponse.errorResponse(exception.convertToOpenapiError()));
        }
        complete();
    }

    private void complete() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        recordMetric("ttlt", DateTimeUtils.getCurrentMills() - startTime);
        byteSender.close();
        if (logger != null && processData != null) {
            logger.log(processData);
        }
    }

    private void recordMetric(String key, long value) {
        if (processData != null && processData.getMetrics() != null) {
            processData.getMetrics().put(key, value);
        }
    }
}
