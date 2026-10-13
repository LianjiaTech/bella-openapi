package com.ke.bella.openapi.protocol.tts;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.BellaStreamCallback;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component("MiniMaxTts")
public class MiniMaxAdaptor implements TtsAdaptor<MiniMaxProperty> {

    @Override
    public byte[] tts(TtsRequest request, String url, MiniMaxProperty property) {
        MiniMaxRequest miniMaxRequest = MiniMaxRequest.from(request, property, false);
        Request httpRequest = buildHttpRequest(url, miniMaxRequest, property);
        clearLargeData(request, miniMaxRequest);
        MiniMaxResponse response = HttpUtils.httpRequest(httpRequest, MiniMaxResponse.class,
                (channelResponse, httpResponse) -> {
                    throw toChannelException(channelResponse, httpResponse.code(), httpResponse.message());
                });
        return decodeAudio(response);
    }

    @Override
    public void streamTts(TtsRequest request, String url, MiniMaxProperty property, Callbacks.StreamCallback callback) {
        MiniMaxRequest miniMaxRequest = MiniMaxRequest.from(request, property, true);
        Request httpRequest = buildHttpRequest(url, miniMaxRequest, property);
        clearLargeData(request, miniMaxRequest);
        HttpUtils.streamRequest(httpRequest, new BellaStreamCallback((Callbacks.HttpStreamTtsCallback) callback));
    }

    @Override
    public Callbacks.StreamCallback buildCallback(TtsRequest request, Callbacks.Sender byteSender,
            EndpointProcessData processData, EndpointLogger logger) {
        return new MiniMaxStreamTtsCallback(byteSender, processData, logger);
    }

    @Override
    public String getDescription() {
        return "MiniMax TTS协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return MiniMaxProperty.class;
    }

    private Request buildHttpRequest(String url, MiniMaxRequest request, MiniMaxProperty property) {
        return authorizationRequestBuilder(property.getAuth())
                .url(withGroupId(url, property.getGroupId()))
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request)))
                .build();
    }

    private String withGroupId(String url, String groupId) {
        if (StringUtils.isBlank(groupId)) {
            return url;
        }
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            return url;
        }
        return httpUrl.newBuilder().setQueryParameter("GroupId", groupId).build().toString();
    }

    static byte[] decodeAudio(MiniMaxResponse response) {
        if (response == null) {
            throw new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.getReasonPhrase(), "MiniMax TTS returned empty response");
        }
        if (!response.isSuccess()) {
            throw toChannelException(response, HttpStatus.BAD_GATEWAY.value(), HttpStatus.BAD_GATEWAY.getReasonPhrase());
        }
        if (response.getData() == null || StringUtils.isBlank(response.getData().getAudio())) {
            throw new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.getReasonPhrase(), "No audio data in MiniMax TTS response");
        }
        return decodeHex(response.getData().getAudio());
    }

    static byte[] decodeHex(String hex) {
        String value = hex.trim();
        if ((value.length() & 1) == 1) {
            throw new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                    HttpStatus.BAD_GATEWAY.getReasonPhrase(), "Invalid hex audio in MiniMax TTS response");
        }
        byte[] data = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new BellaException.ChannelException(HttpStatus.BAD_GATEWAY.value(),
                        HttpStatus.BAD_GATEWAY.getReasonPhrase(), "Invalid hex audio in MiniMax TTS response");
            }
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }

    static BellaException.ChannelException toChannelException(MiniMaxResponse response, int httpCode, String httpMessage) {
        if (response != null && response.getBaseResp() != null) {
            Integer statusCode = response.getBaseResp().getStatusCode();
            String message = StringUtils.defaultIfBlank(response.getBaseResp().getStatusMsg(), response.errorMessage());
            HttpStatus status = mapStatus(statusCode, httpCode);
            return new BellaException.ChannelException(status.value(), status.getReasonPhrase(), message);
        }
        return new BellaException.ChannelException(httpCode, httpMessage);
    }

    private static HttpStatus mapStatus(Integer miniMaxStatusCode, int httpCode) {
        if (miniMaxStatusCode != null) {
            switch (miniMaxStatusCode) {
            case 1001:
                return HttpStatus.GATEWAY_TIMEOUT;
            case 1002:
            case 1039:
                return HttpStatus.TOO_MANY_REQUESTS;
            case 1004:
                return HttpStatus.UNAUTHORIZED;
            case 1042:
            case 2013:
                return HttpStatus.BAD_REQUEST;
            case 1000:
            default:
                return HttpStatus.BAD_GATEWAY;
            }
        }
        if (httpCode == HttpStatus.UNAUTHORIZED.value() || httpCode == HttpStatus.FORBIDDEN.value()) {
            return HttpStatus.valueOf(httpCode);
        }
        if (httpCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (httpCode >= 400 && httpCode < 500) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
