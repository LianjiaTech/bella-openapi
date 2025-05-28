package com.ke.bella.openapi.protocol.realtime;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.BellaWebSocketListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component("KeRealtime")
public class KeAdaptor implements RealTimeAdaptor<RealtimeProperty> {

    @Override
    public WebSocket startTranscription(String url, RealtimeProperty property, RealTimeMessage request, Callbacks.WebSocketCallback callback) {
        String apikey = request.getApikey();
        if(property.getAuth() != null && StringUtils.isNotEmpty(property.getAuth().getApiKey())) {
            apikey = property.getAuth().getApiKey();
        }
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", apikey);
        WebSocket webSocket = HttpUtils.websocketRequest(builder.build(), new BellaWebSocketListener(callback));
        if(request.getPayload() != null) {
            if(property.getTtsOption() != null) {
                if(request.getPayload().getTtsOption() == null) {
                    request.getPayload().setTtsOption(property.getTtsOption());
                } else if(request.getPayload().getTtsOption().getModel() == null) {
                    request.getPayload().getTtsOption().setModel(property.getTtsOption().getModel());
                }
            }
            if(property.getLlmOption() != null && property.getLlmOption().getMain() != null) {
                if(request.getPayload().getLlmOption() == null) {
                    request.getPayload().setLlmOption(property.getLlmOption());
                } else if(request.getPayload().getLlmOption().getMain() == null) {
                    request.getPayload().getLlmOption().setMain(property.getLlmOption().getMain());
                } else if(request.getPayload().getLlmOption().getMain().getModel() == null) {
                    request.getPayload().getLlmOption().getMain().setModel(property.getLlmOption().getMain().getModel());
                }
            }
        }
        webSocket.send(JacksonUtils.serialize(request));
        callback.started();
        return webSocket;
    }

    @Override
    public boolean sendAudioData(WebSocket webSocket, byte[] audioData, Callbacks.WebSocketCallback callback) {
        return webSocket.send(ByteString.of(audioData));
    }

    @Override
    public boolean stopTranscription(WebSocket webSocket, RealTimeMessage request, Callbacks.WebSocketCallback callback) {
        return webSocket.send(JacksonUtils.serialize(request));
    }

    @Override
    public void closeConnection(WebSocket webSocket) {
        webSocket.close(1000, "client close");
    }

    @Override
    public Callbacks.WebSocketCallback createCallback(Callbacks.Sender sender, EndpointProcessData processData, EndpointLogger logger,
            String taskId, RealTimeMessage request, RealtimeProperty property) {
        return new KeRealtimeCallback(sender, processData, logger, taskId);
    }

    @Override
    public String getDescription() {
        return "贝壳协议";
    }

    @Override
    public Class<RealtimeProperty> getPropertyClass() {
        return RealtimeProperty.class;
    }
}
