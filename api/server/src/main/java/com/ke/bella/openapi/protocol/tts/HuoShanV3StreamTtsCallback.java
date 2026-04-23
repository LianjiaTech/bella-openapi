package com.ke.bella.openapi.protocol.tts;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import org.springframework.http.HttpStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * 火山 V3 HTTP Chunked TTS 流式回调。
 * 响应格式为每行一个 JSON: {"code":0,"message":"","data":"base64音频"}
 */
@Slf4j
public class HuoShanV3StreamTtsCallback implements Callbacks.HttpStreamTtsCallback {

    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final Callbacks.Sender byteSender;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;

    private boolean first = true;
    private final long startTime = DateTimeUtils.getCurrentMills();
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

    public HuoShanV3StreamTtsCallback(Callbacks.Sender byteSender, EndpointProcessData processData, EndpointLogger logger) {
        this.byteSender = byteSender;
        this.processData = processData;
        this.logger = logger;
        processData.setMetrics(new HashMap<>());
    }

    @Override
    public void onOpen() {
    }

    @Override
    public void callback(byte[] msg) {
        for (byte b : msg) {
            if (b == '\n') {
                processLine();
                lineBuffer.reset();
            } else {
                lineBuffer.write(b);
            }
        }
    }

    private void processLine() {
        if (lineBuffer.size() == 0) {
            return;
        }
        String line = lineBuffer.toString().trim();
        if (line.isEmpty() || !line.startsWith("{")) {
            return;
        }
        try {
            HuoShanV3Response response = JacksonUtils.deserialize(line, HuoShanV3Response.class);
            if (response == null) {
                return;
            }
            if (!response.isSuccess()) {
                log.warn("HuoShanV3 stream error: code={}, message={}", response.getCode(), response.getMessage());
                HttpStatus status = HuoShanV3Adaptor.mapErrorCode(response.getCode());
                finish(new BellaException.ChannelException(status.value(), status.getReasonPhrase(), response.getMessage()));
                return;
            }
            if (response.getData() != null && !response.getData().isEmpty()) {
                byte[] audioBytes = BASE64_DECODER.decode(response.getData());
                byteSender.send(audioBytes);
                if (first) {
                    processData.getMetrics().put("ttft", DateTimeUtils.getCurrentMills() - startTime);
                    first = false;
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse HuoShanV3 data: {}", line, e);
        }
    }

    protected void flushLineBuffer() {
        if (lineBuffer.size() > 0) {
            processLine();
            lineBuffer.reset();
        }
    }

    @Override
    public void finish() {
        flushLineBuffer();
        processData.getMetrics().put("ttlt", DateTimeUtils.getCurrentMills() - startTime);
        byteSender.close();
        logger.log(processData);
    }

    @Override
    public void finish(BellaException exception) {
        processData.setResponse(OpenapiResponse.errorResponse(exception.convertToOpenapiError()));
        finish();
    }
}
