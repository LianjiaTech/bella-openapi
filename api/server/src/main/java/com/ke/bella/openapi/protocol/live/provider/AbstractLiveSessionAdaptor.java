package com.ke.bella.openapi.protocol.live.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.BellaWebSocketListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.live.LiveSessionMessage;
import com.ke.bella.openapi.protocol.live.LiveSessionProperty;
import com.ke.bella.openapi.protocol.live.session.LiveProviderEvent;
import com.ke.bella.openapi.protocol.live.session.LiveSessionListener;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractLiveSessionAdaptor implements LiveSessionAdaptor<LiveSessionProperty> {
    private final Set<WebSocket> closingSockets = ConcurrentHashMap.newKeySet();

    @Override
    public WebSocket connect(String url, LiveSessionMessage start, LiveSessionProperty property, LiveSessionListener listener) {
        if(StringUtils.isBlank(url)) {
            throw new BellaException.ChannelException(503, "Service Unavailable", "Live upstream URL is required");
        }
        Request.Builder builder = authorizationRequestBuilder(property == null ? null : property.getAuth()).url(resolveUrl(url, property));
        customizeRequest(builder, property);
        ProviderCallback callback = new ProviderCallback(listener);
        WebSocket webSocket = HttpUtils.websocketRequest(builder.build(), new BellaWebSocketListener(callback) {
            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                closingSockets.remove(webSocket);
                super.onClosed(webSocket, code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                if(closingSockets.remove(webSocket)) {
                    return;
                }
                super.onFailure(webSocket, throwable, response);
            }
        });
        if(!webSocket.send(JacksonUtils.serialize(toProvider(start, property)))) {
            webSocket.cancel();
            throw new BellaException.ChannelException(503, "Service Unavailable", "Failed to initialize Live upstream");
        }
        return webSocket;
    }

    @Override
    public boolean send(WebSocket webSocket, LiveSessionMessage message) {
        return webSocket != null && webSocket.send(JacksonUtils.serialize(toProvider(message, null)));
    }

    @Override
    public void close(WebSocket webSocket, String reason) {
        if(webSocket == null) {
            return;
        }
        closingSockets.add(webSocket);
        if(!webSocket.close(1000, reason)) {
            closingSockets.remove(webSocket);
            webSocket.cancel();
        }
    }

    protected void customizeRequest(Request.Builder builder, LiveSessionProperty property) {
    }

    protected String resolveUrl(String url, LiveSessionProperty property) {
        return url;
    }

    protected abstract Object toProvider(LiveSessionMessage message, LiveSessionProperty property);

    protected abstract List<LiveSessionMessage> fromProvider(JsonNode message);

    protected String providerEventType(JsonNode message) {
        if(message == null) {
            return "unknown";
        }
        String type = message.path("type").asText(null);
        if(StringUtils.isNotBlank(type)) {
            return type;
        }
        Iterator<String> fields = message.fieldNames();
        return fields.hasNext() ? fields.next() : "unknown";
    }

    @Override
    public Class<LiveSessionProperty> getPropertyClass() {
        return LiveSessionProperty.class;
    }

    private final class ProviderCallback implements Callbacks.WebSocketCallback {
        private final LiveSessionListener listener;

        private ProviderCallback(LiveSessionListener listener) {
            this.listener = listener;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            onMessage(webSocket, bytes.utf8());
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            long receivedNanos = System.nanoTime();
            JsonNode node = JacksonUtils.deserialize(text);
            List<LiveSessionMessage> converted = node == null ? null : fromProvider(node);
            long convertedNanos = System.nanoTime();
            int normalizedMessageCount = converted == null ? 0 : converted.size();
            LiveProviderEvent event = new LiveProviderEvent(providerEventType(node), text.length(), normalizedMessageCount,
                    receivedNanos, convertedNanos);
            listener.onProviderEvent(event);
            if(converted != null) {
                converted.stream().filter(message -> message != null).forEach(message -> listener.onProviderMessage(message, event));
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            listener.onProviderClosing(code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            listener.onProviderClosed(code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
            listener.onProviderFailure(throwable == null ? new IllegalStateException("Live upstream failed") : throwable);
        }

        @Override
        public boolean started() {
            return true;
        }
    }
}
