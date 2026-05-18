package com.ke.bella.openapi.protocol.tts;

import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    @Override
    public byte[] tts(TtsRequest request, String url, HuoShanV3Property property) {
        HuoShanV3Request v3Request = HuoShanV3Request.from(request, property);
        Request httpRequest = buildHttpRequest(url, v3Request, property);
        clearLargeData(request, v3Request);
        AudioCollector collector = new AudioCollector();
        HttpUtils.streamRequest(httpRequest, new BellaStreamCallback(collector.getDelegate()));
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
     * 非流式音频收集器：复用 HuoShanV3StreamTtsCallback 的行解析逻辑，
     * 通过自定义 Sender 将音频字节收集到 buffer 中。
     */
    private static class AudioCollector {
        private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        private final CompletableFuture<Void> doneFuture = new CompletableFuture<>();
        private volatile BellaException error;

        private final Callbacks.Sender bufferSender = new Callbacks.Sender() {
            @Override
            public void send(String text) {
            }

            @Override
            public void send(byte[] bytes) {
                audioBuffer.write(bytes, 0, bytes.length);
            }

            @Override
            public void onError(Throwable e) {
                error = BellaException.fromException(e);
            }

            @Override
            public void close() {
                doneFuture.complete(null);
            }
        };

        private final HuoShanV3StreamTtsCallback delegate = new HuoShanV3StreamTtsCallback(bufferSender, null, null) {
            @Override
            public void finish() {
                // 只做行缓冲刷新和关闭 sender，跳过 metrics 和 logger
                flushLineBuffer();
                bufferSender.close();
            }

            @Override
            public void finish(BellaException exception) {
                error = exception;
                finish();
            }
        };

        public HuoShanV3StreamTtsCallback getDelegate() {
            return delegate;
        }

        public byte[] getAudioBytes() {
            try {
                doneFuture.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new BellaException.ChannelException(HttpStatus.GATEWAY_TIMEOUT.value(),
                        HttpStatus.GATEWAY_TIMEOUT.getReasonPhrase(), "TTS request timed out after 30s");
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
