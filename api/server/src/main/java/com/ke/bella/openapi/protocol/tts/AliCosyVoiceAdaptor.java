package com.ke.bella.openapi.protocol.tts;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.BellaStreamCallback;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component("AliCosyVoiceTts")
public class AliCosyVoiceAdaptor implements TtsAdaptor<AliCosyVoiceProperty> {

    @Override
    public byte[] tts(TtsRequest request, String url, AliCosyVoiceProperty property) {
        AliCosyVoiceRequest aliRequest = AliCosyVoiceRequest.from(request, property);
        Request httpRequest = buildHttpRequest(url, aliRequest, property, false);
        clearLargeData(request, aliRequest);
        AliCosyVoiceResponse response = requestSynthesis(httpRequest);
        return downloadAudio(resolveAudioUrl(response));
    }

    @Override
    public void streamTts(TtsRequest request, String url, AliCosyVoiceProperty property, Callbacks.StreamCallback callback) {
        AliCosyVoiceRequest aliRequest = AliCosyVoiceRequest.from(request, property);
        Request httpRequest = buildHttpRequest(url, aliRequest, property, true);
        clearLargeData(request, aliRequest);
        HttpUtils.streamRequest(httpRequest, new BellaStreamCallback((Callbacks.HttpStreamTtsCallback) callback));
    }

    @Override
    public Callbacks.StreamCallback buildCallback(TtsRequest request, Callbacks.Sender byteSender,
            EndpointProcessData processData, EndpointLogger logger) {
        return new AliCosyVoiceStreamTtsCallback(byteSender, processData, logger);
    }

    @Override
    public String getDescription() {
        return "Ali CosyVoice TTS protocol";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AliCosyVoiceProperty.class;
    }

    private Request buildHttpRequest(String url, AliCosyVoiceRequest request, AliCosyVoiceProperty property, boolean stream) {
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .header("Content-Type", "application/json");
        if(stream) {
            builder.header("X-DashScope-SSE", "enable");
        }
        return builder.post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request))).build();
    }

    protected AliCosyVoiceResponse requestSynthesis(Request httpRequest) {
        AliCosyVoiceResponse response = HttpUtils.httpRequest(httpRequest, AliCosyVoiceResponse.class,
                (channelResponse, httpResponse) -> {
                    throw toChannelException(channelResponse, httpResponse.code(), httpResponse.message());
                });
        if(response == null) {
            throw new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.getReasonPhrase(), "Ali CosyVoice TTS returned empty response");
        }
        if(!response.isSuccess()) {
            throw toChannelException(response, HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY.getReasonPhrase());
        }
        return response;
    }

    protected byte[] downloadAudio(String audioUrl) {
        Request request = new Request.Builder().url(audioUrl).get().build();
        try(Response response = HttpUtils.httpRequest(request)) {
            if(!response.isSuccessful()) {
                throw toChannelException(null, response.code(), response.message());
            }
            ResponseBody body = response.body();
            if(body == null) {
                throw new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                        HttpStatus.BAD_GATEWAY.getReasonPhrase(), "Ali CosyVoice audio URL returned empty response");
            }
            byte[] bytes = body.bytes();
            if(bytes.length == 0) {
                throw new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                        HttpStatus.BAD_GATEWAY.getReasonPhrase(), "Ali CosyVoice audio URL returned empty audio");
            }
            return bytes;
        } catch (IOException e) {
            throw BellaException.fromException(e);
        }
    }

    static String resolveAudioUrl(AliCosyVoiceResponse response) {
        if(response == null) {
            throw new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.getReasonPhrase(), "Ali CosyVoice TTS returned empty response");
        }
        if(!response.isSuccess()) {
            throw toChannelException(response, HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY.getReasonPhrase());
        }
        if(response.getOutput() == null || response.getOutput().getAudio() == null
                || StringUtils.isBlank(response.getOutput().getAudio().getUrl())) {
            throw new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.getReasonPhrase(), "No audio URL in Ali CosyVoice TTS response");
        }
        return response.getOutput().getAudio().getUrl();
    }

    static BellaException.ChannelException toChannelException(AliCosyVoiceResponse response, int httpCode, String httpMessage) {
        String message = response == null ? httpMessage : response.errorMessage();
        HttpStatus status = mapStatus(httpCode);
        return new BellaException.ChannelException(status.value(), status.getReasonPhrase(), message);
    }

    private static HttpStatus mapStatus(int httpCode) {
        if(httpCode == HttpStatus.UNAUTHORIZED.value() || httpCode == HttpStatus.FORBIDDEN.value()) {
            return HttpStatus.valueOf(httpCode);
        }
        if(httpCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if(httpCode >= 400 && httpCode < 500) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
