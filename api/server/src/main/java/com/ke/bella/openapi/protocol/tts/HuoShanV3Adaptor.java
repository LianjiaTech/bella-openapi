package com.ke.bella.openapi.protocol.tts;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.ke.bella.openapi.common.exception.BellaException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.BellaStreamCallback;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 火山引擎 V3 HTTP Chunked TTS 适配器。
 * 响应格式为每行一个 JSON，通过 BellaStreamCallback 流式读取并解析。
 */
@Slf4j
@Component("HuoShanV3Tts")
public class HuoShanV3Adaptor implements TtsAdaptor<HuoShanV3Property> {

    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    @Override
    public byte[] tts(TtsRequest request, String url, HuoShanV3Property property) {
        HuoShanV3Request v3Request = HuoShanV3Request.from(request, property);
        Request httpRequest = buildHttpRequest(url, v3Request, property);
        clearLargeData(request, v3Request);
        AudioCollector collector = new AudioCollector();
        HttpUtils.streamRequest(httpRequest, new BellaStreamCallback(collector));
        return collector.getAudioBytes();
    }

    @Override
    public void streamTts(TtsRequest request, String url, HuoShanV3Property property, Callbacks.StreamCallback callback) {
        HuoShanV3Request v3Request = HuoShanV3Request.from(request, property);
        Request httpRequest = buildHttpRequest(url, v3Request, property);
        clearLargeData(request, v3Request);
        HttpUtils.streamRequest(httpRequest, new BellaStreamCallback((Callbacks.HttpStreamTtsCallback) callback));
    }

    @Override
    public Callbacks.StreamCallback buildCallback(TtsRequest request, Callbacks.Sender byteSender,
            EndpointProcessData processData, EndpointLogger logger) {
        return new HuoShanV3StreamTtsCallback(byteSender, processData, logger);
    }

    @Override
    public String getDescription() {
        return "火山V3协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return HuoShanV3Property.class;
    }

    private Request buildHttpRequest(String url, HuoShanV3Request v3Request, HuoShanV3Property property) {
        return new Request.Builder()
                .url(url)
                .header("X-Api-App-Id", property.getAppId())
                .header("X-Api-Access-Key", property.getAccessKey())
                .header("X-Api-Resource-Id", property.getResourceId())
                .header("X-Api-Request-Id", UUID.randomUUID().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(v3Request)))
                .build();
    }

    static HttpStatus mapErrorCode(int code) {
        if (code == HuoShanV3Response.CODE_TEXT_LIMIT) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        } else if (code == HuoShanV3Response.CODE_PERMISSION) {
            return HttpStatus.FORBIDDEN;
        } else if (code == HuoShanV3Response.CODE_SERVER_ERROR) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 非流式音频收集器：通过 BellaStreamCallback 接收 HTTP Chunked 字节流，
     * 按行解析 JSON，收集所有音频 base64 数据拼接后返回。
     */
    private class AudioCollector implements Callbacks.HttpStreamTtsCallback {
        private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        private final CompletableFuture<Void> doneFuture = new CompletableFuture<>();
        private volatile BellaException error;

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
                    HttpStatus status = mapErrorCode(response.getCode());
                    error = new BellaException.ChannelException(status.value(), status.getReasonPhrase(), response.getMessage());
                    return;
                }
                if (response.getData() != null && !response.getData().isEmpty()) {
                    byte[] audioBytes = BASE64_DECODER.decode(response.getData());
                    audioBuffer.write(audioBytes, 0, audioBytes.length);
                }
            } catch (Exception e) {
                log.error("Failed to parse HuoShanV3 data: {}", line, e);
            }
        }

        @Override
        public void finish() {
            if (lineBuffer.size() > 0) {
                processLine();
                lineBuffer.reset();
            }
            doneFuture.complete(null);
        }

        @Override
        public void finish(BellaException exception) {
            this.error = exception;
            finish();
        }

        public byte[] getAudioBytes() {
            try {
                doneFuture.get();
            } catch (Exception e) {
                throw BellaException.fromException(e);
            }
            if (error != null) {
                throw error;
            }
            byte[] result = audioBuffer.toByteArray();
            if (result.length == 0) {
                throw new BellaException.ChannelException(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "No audio data in response");
            }
            return result;
        }
    }
}
