package com.ke.bella.openapi.protocol.tts.realtime.huoshan;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.BellaWebSocketListener;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.Callbacks.WebSocketCallback;
import com.ke.bella.openapi.protocol.tts.realtime.HuoshanRealtimeTtsProperty;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsAdaptor;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsCapability;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsMessage;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsTaskContext;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsTaskListener;
import com.ke.bella.openapi.protocol.tts.realtime.SupportLevel;
import com.ke.bella.openapi.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.WebSocket;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component("HuoshanRealtimeTtsAdaptor")
public class HuoshanRealtimeTtsAdaptor implements RealtimeTtsAdaptor<HuoshanRealtimeTtsProperty> {
    private final HuoshanTtsWsCodec codec = new HuoshanTtsWsCodec();

    @Override
    public WebSocket startSpeech(String url, HuoshanRealtimeTtsProperty property, RealtimeTtsMessage request, WebSocketCallback callback) {
        return startConnection(url, property, request, callback);
    }

    @Override
    public WebSocket startConnection(String url, HuoshanRealtimeTtsProperty property, RealtimeTtsMessage request, WebSocketCallback callback) {
        String requestId = UUID.randomUUID().toString();
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("X-Api-Resource-Id", property.getResourceId());
        applyAuth(builder, property, requestId);
        Request webSocketRequest = builder.build();
        return HttpUtils.websocketRequest(webSocketRequest, new BellaWebSocketListener(callback));
    }

    @Override
    public boolean startSession(WebSocket webSocket, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
        if(callback instanceof HuoshanRealtimeTtsCallback) {
            ((HuoshanRealtimeTtsCallback) callback).markSessionStartSent(taskContext);
        }
        return webSocket.send(codec.startSessionFrame(taskContext.getProviderSessionId(), taskContext.getFrozenPayload()));
    }

    @Override
    public boolean inputText(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
        return webSocket.send(codec.taskRequestFrame(sessionId(callback), request));
    }

    @Override
    public boolean inputText(WebSocket webSocket, RealtimeTtsMessage request, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
        return webSocket.send(codec.taskRequestFrame(taskContext.getProviderSessionId(), request));
    }

    @Override
    public boolean finishSpeech(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
        return webSocket.send(codec.finishSessionFrame(sessionId(callback)));
    }

    @Override
    public boolean finishSpeech(WebSocket webSocket, RealtimeTtsMessage request, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
        return webSocket.send(codec.finishSessionFrame(taskContext.getProviderSessionId()));
    }

    @Override
    public SupportLevel cancelSpeech(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
        boolean sent = webSocket.send(codec.cancelSessionFrame(sessionId(callback)));
        if(sent && callback instanceof HuoshanRealtimeTtsCallback) {
            ((HuoshanRealtimeTtsCallback) callback).markCancelRequested();
        }
        return sent ? SupportLevel.SUPPORTED : SupportLevel.CLOSE_CONNECTION;
    }

    @Override
    public SupportLevel cancelSpeech(WebSocket webSocket, RealtimeTtsMessage request, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
        boolean sent = webSocket.send(codec.cancelSessionFrame(taskContext.getProviderSessionId()));
        if(sent && callback instanceof HuoshanRealtimeTtsCallback) {
            ((HuoshanRealtimeTtsCallback) callback).markCancelRequested();
        }
        return sent ? SupportLevel.SUPPORTED : SupportLevel.CLOSE_CONNECTION;
    }

    @Override
    public SupportLevel clearTextBuffer(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
        return SupportLevel.UNSUPPORTED;
    }

    @Override
    public void closeConnection(WebSocket webSocket) {
        try {
            webSocket.send(codec.finishConnectionFrame());
        } catch (Exception e) {
            log.warn("send huoshan FinishConnection failed: {}", e.getMessage());
        }
        webSocket.close(1000, "client closed");
    }

    @Override
    public RealtimeTtsCapability capability(RealtimeTtsMessage request) {
        return RealtimeTtsCapability.huoshan(request.getPayload() != null && Boolean.TRUE.equals(request.getPayload().getEnableTimestamp()));
    }

    @Override
    public WebSocketCallback createCallback(RealtimeTtsTaskListener listener, RealtimeTtsTaskContext taskContext, HuoshanRealtimeTtsProperty property) {
        return new HuoshanRealtimeTtsCallback(codec, listener, taskContext);
    }

    @Override
    public String getDescription() {
        return "火山/豆包双向TTS WebSocket协议";
    }

    @Override
    public Class<HuoshanRealtimeTtsProperty> getPropertyClass() {
        return HuoshanRealtimeTtsProperty.class;
    }

    private void applyAuth(Request.Builder builder, HuoshanRealtimeTtsProperty property, String requestId) {
        AuthorizationProperty auth = property.getAuth();
        if(hasLegacyAuth(auth)) {
            String accessToken = legacyAccessToken(auth);
            if(StringUtils.isBlank(property.getAppId()) || StringUtils.isBlank(accessToken)) {
                throw new BellaException.ChannelException(503, "Service Unavailable",
                        "Huoshan legacy realtime TTS requires appId and auth.secret/auth.apiKey");
            }
            builder.header("X-Api-App-Id", property.getAppId());
            builder.header("X-Api-Access-Key", accessToken);
            builder.header("X-Api-Request-Id", requestId);
            return;
        }

        if(StringUtils.isBlank(property.getAccessKey())) {
            throw new BellaException.ChannelException(503, "Service Unavailable", "Huoshan realtime TTS requires accessKey for X-Api-Key");
        }
        builder.header("X-Api-Key", property.getAccessKey());
        builder.header("X-Api-Connect-Id", requestId);
    }

    private boolean hasLegacyAuth(AuthorizationProperty auth) {
        return auth != null && (auth.getType() != null
                || StringUtils.isNotBlank(auth.getHeader())
                || StringUtils.isNotBlank(auth.getApiKey())
                || StringUtils.isNotBlank(auth.getSecret()));
    }

    private String legacyAccessToken(AuthorizationProperty auth) {
        if(auth == null) {
            return null;
        }
        return StringUtils.defaultIfBlank(auth.getSecret(), auth.getApiKey());
    }

    private String sessionId(WebSocketCallback callback) {
        if(callback instanceof HuoshanRealtimeTtsCallback) {
            return ((HuoshanRealtimeTtsCallback) callback).getTaskContext().getProviderSessionId();
        }
        return null;
    }
}
