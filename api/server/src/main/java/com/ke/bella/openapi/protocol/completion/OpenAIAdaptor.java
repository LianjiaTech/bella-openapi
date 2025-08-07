package com.ke.bella.openapi.protocol.completion;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.Callbacks.StreamCompletionCallback;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.Arrays;

@Component("OpenAICompletion")
public class OpenAIAdaptor implements CompletionAdaptorDelegator<OpenAIProperty> {

    public final Callbacks.SseEventConverter<StreamCompletionResponse> sseConverter = new Callbacks.DefaultSseConverter();

    Callbacks.ChannelErrorCallback<CompletionResponse> errorCallback = (errorResponse, res) -> {
        if(errorResponse.getError() != null) {
            errorResponse.getError().setHttpCode(res.code());
        }
    };

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.HttpDelegator delegator) {
        CompletionResponse response;
        if(delegator == null) {
            Request httpRequest = buildRequest(request, url, property);
            response = HttpUtils.httpRequest(httpRequest, CompletionResponse.class, errorCallback);
        } else {
            response = delegator.request(request, CompletionResponse.class, errorCallback);
        }
        ResponseHelper.splitReasoningFromContent(response, property);
        response.setCreated(DateTimeUtils.getCurrentSeconds());
        return response;
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, StreamCompletionCallback callback,
            Callbacks.StreamDelegator delegator) {
        CompletionSseListener listener = new CompletionSseListener(callback, sseConverter);
        if(delegator == null) {
            Request httpRequest = buildRequest(request, url, property);
            HttpUtils.streamRequest(httpRequest, listener);
        } else {
            delegator.request(request, listener);
        }
    }
    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property) {
        return completion(request, url, property, null);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, StreamCompletionCallback callback) {
        streamCompletion(request, url, property, callback, null);
    }

    private Request buildRequest(CompletionRequest request, String url, OpenAIProperty property) {
        if(property.supportStreamOptions && request.isStream()) {
            request.setStream_options(new CompletionRequest.StreamOptions());
        }
        if(MapUtils.isNotEmpty(request.getExtra_body()) && property.getAbandonFields() != null) {
             Arrays.stream(property.getAbandonFields()).forEach(f -> request.getExtra_body().remove(f));
        }
        request.setModel(property.getDeployName());
        if(StringUtils.isNotEmpty(property.getApiVersion())) {
            url += property.getApiVersion();
        }
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.serialize(request)));
        if(MapUtils.isNotEmpty(property.getExtraHeaders())) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }
        return builder.build();
    }

    @Override
    public String getDescription() {
        return "OpenAI协议";
    }

    @Override
    public Class<OpenAIProperty> getPropertyClass() {
        return OpenAIProperty.class;
    }
}
