package com.ke.bella.openapi.protocol.realtime;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.Callbacks.WebSocketCallback;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import okhttp3.WebSocket;

public interface RealTimeAdaptor<T extends AsrProperty> extends IProtocolAdaptor {

    @Override
    default String endpoint() {
        return "/v1/audio/realtime";
    }

    WebSocket startTranscription(String url, T property, RealTimeMessage request, WebSocketCallback callback);

    boolean sendAudioData(WebSocket webSocket, byte[] audioData, WebSocketCallback callback);

    boolean stopTranscription(WebSocket webSocket, RealTimeMessage request, WebSocketCallback callback);

    void closeConnection(WebSocket webSocket);

    WebSocketCallback createCallback(Callbacks.Sender sender, EndpointProcessData processData, EndpointLogger logger, String taskId,
            RealTimeMessage request, T property);

    Class<T> getPropertyClass();
}
