package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.protocol.Callbacks.WebSocketCallback;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import okhttp3.WebSocket;

public interface RealtimeTtsAdaptor<T extends com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsProperty> extends IProtocolAdaptor {
    WebSocket startSpeech(String url, T property, RealtimeTtsMessage request, WebSocketCallback callback);

    default WebSocket startConnection(String url, T property, RealtimeTtsMessage request, WebSocketCallback callback) {
        return startSpeech(url, property, request, callback);
    }

    default boolean startSession(WebSocket webSocket, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
        return true;
    }

    boolean inputText(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback);

    default boolean inputText(WebSocket webSocket, RealtimeTtsMessage request, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
        return inputText(webSocket, request, callback);
    }

    boolean finishSpeech(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback);

    default boolean finishSpeech(WebSocket webSocket, RealtimeTtsMessage request, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
        return finishSpeech(webSocket, request, callback);
    }

    SupportLevel cancelSpeech(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback);

    default SupportLevel cancelSpeech(WebSocket webSocket, RealtimeTtsMessage request, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
        return cancelSpeech(webSocket, request, callback);
    }

    SupportLevel clearTextBuffer(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback);

    default SupportLevel clearTextBuffer(WebSocket webSocket, RealtimeTtsMessage request, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
        return clearTextBuffer(webSocket, request, callback);
    }

    void closeConnection(WebSocket webSocket);

    RealtimeTtsCapability capability(RealtimeTtsMessage request);

    WebSocketCallback createCallback(RealtimeTtsTaskListener listener, RealtimeTtsTaskContext taskContext, T property);

    @Override
    default String endpoint() {
        return RealtimeTtsConstants.ENDPOINT;
    }

    @SuppressWarnings("unchecked")
    @Override
    Class<T> getPropertyClass();
}
