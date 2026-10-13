package com.ke.bella.openapi.protocol.live.provider.mock;

import com.ke.bella.openapi.protocol.live.LiveSessionMessage;
import com.ke.bella.openapi.protocol.live.LiveSessionEventType;
import com.ke.bella.openapi.protocol.live.LiveSessionProperty;
import com.ke.bella.openapi.protocol.live.session.LiveSessionConstants;
import com.ke.bella.openapi.protocol.live.session.LiveSessionListener;
import com.ke.bella.openapi.protocol.live.provider.LiveSessionAdaptor;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;
import org.springframework.stereotype.Component;

@Component
public class MockAdaptor implements LiveSessionAdaptor<LiveSessionProperty> {
    @Override
    public WebSocket connect(String url, LiveSessionMessage start, LiveSessionProperty property, LiveSessionListener listener) {
        MockWebSocket webSocket = new MockWebSocket(listener);
        LiveSessionMessage started = LiveSessionMessage.event(LiveSessionEventType.SESSION_STARTED, "mock-provider-session");
        started.setModel(start.resolveModel());
        listener.onProviderMessage(started);
        return webSocket;
    }

    @Override
    public boolean send(WebSocket webSocket, LiveSessionMessage message) {
        return webSocket.send(com.ke.bella.openapi.utils.JacksonUtils.serialize(message));
    }

    @Override
    public String endpoint() {
        return LiveSessionConstants.ENDPOINT;
    }

    @Override
    public String getDescription() {
        return "mock协议";
    }

    @Override
    public Class<LiveSessionProperty> getPropertyClass() {
        return LiveSessionProperty.class;
    }

    private static final class MockWebSocket implements WebSocket {
        private final LiveSessionListener listener;
        private boolean closed;

        private MockWebSocket(LiveSessionListener listener) {
            this.listener = listener;
        }

        @Override
        public Request request() {
            return new Request.Builder().url("http://localhost/mock-live").build();
        }

        @Override
        public long queueSize() {
            return 0;
        }

        @Override
        public boolean send(String text) {
            LiveSessionMessage message = com.ke.bella.openapi.utils.JacksonUtils.deserialize(text, LiveSessionMessage.class);
            if(message == null || closed) {
                return false;
            }
            if(message.eventType() == LiveSessionEventType.INPUT_TEXT_APPEND) {
                LiveSessionMessage response = LiveSessionMessage.event(LiveSessionEventType.OUTPUT_TEXT_DELTA, null);
                response.setDelta(message.payloadValue("text"));
                listener.onProviderMessage(response);
            } else if(message.eventType() == LiveSessionEventType.SESSION_CLOSE) {
                listener.onProviderMessage(LiveSessionMessage.event(LiveSessionEventType.SESSION_CLOSED, null));
            }
            return true;
        }

        @Override
        public boolean send(ByteString bytes) {
            return send(bytes.utf8());
        }

        @Override
        public boolean close(int code, String reason) {
            closed = true;
            return true;
        }

        @Override
        public void cancel() {
            closed = true;
        }
    }
}
