package com.ke.bella.openapi.protocol.tts;

import com.ke.bella.openapi.protocol.BellaStreamCallback;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@Component("OpenAITts")
public class OpenAIAdaptor implements TtsAdaptor<OpenAIProperty> {
    @Override
    public byte[] tts(TtsRequest request, String url, OpenAIProperty property) {
        Request httpRequest = buildRequest(request, url, property);
        clearLargeData(request);
        return HttpUtils.doHttpRequest(httpRequest);
    }

    @Override
    public void streamTts(TtsRequest request, String url, OpenAIProperty property, Callbacks.StreamCallback callback) {
        Request httpRequest = buildRequest(request, url, property);
        clearLargeData(request);
        HttpUtils.streamRequest(httpRequest, new BellaStreamCallback((Callbacks.HttpStreamTtsCallback) callback));
    }

    @Override
    public Callbacks.StreamCallback buildCallback(TtsRequest request, Callbacks.Sender byteSender,
            EndpointProcessData processData, EndpointLogger logger) {
        return new OpenAIStreamTtsCallback(byteSender, processData, logger);
    }

    @Override
    public String getDescription() {
        return "OpenAI协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return OpenAIProperty.class;
    }

    private Request buildRequest(TtsRequest request, String url, OpenAIProperty property) {
        if(StringUtils.isNotBlank(property.deployName)) {
            request.model = property.deployName;
        }
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request)));
        return builder.build();
    }
}
