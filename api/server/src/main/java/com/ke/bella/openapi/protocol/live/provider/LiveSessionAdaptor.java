package com.ke.bella.openapi.protocol.live.provider;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.live.LiveSessionMessage;
import com.ke.bella.openapi.protocol.live.LiveSessionProperty;
import com.ke.bella.openapi.protocol.live.session.LiveSessionListener;
import okhttp3.WebSocket;

public interface LiveSessionAdaptor<T extends LiveSessionProperty> extends IProtocolAdaptor {
    WebSocket connect(String url, LiveSessionMessage start, T property, LiveSessionListener listener);

    boolean send(WebSocket webSocket, LiveSessionMessage message);

    default void close(WebSocket webSocket, String reason) {
        if(webSocket != null) {
            webSocket.close(1000, reason);
        }
    }
}
