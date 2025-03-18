package com.ke.bella.openapi.protocol.tts;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.AsyncContext;
import java.io.OutputStream;

public interface TtsAdaptor <T extends TtsProperty> extends IProtocolAdaptor {

    byte[] tts(TtsRequest request, String url, T property);

    void streamTts(TtsRequest request, String url, T property, Callbacks.StreamTtsCallback callback);

    Callbacks.StreamTtsCallback buildCallback(TtsRequest request, OutputStream outputStream, AsyncContext context, EndpointProcessData processData, EndpointLogger logger);

    @Override
    default String endpoint() {
        return "/v1/audio/speech";
    }
}
