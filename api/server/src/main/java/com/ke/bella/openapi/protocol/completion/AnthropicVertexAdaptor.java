package com.ke.bella.openapi.protocol.completion;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.TransferToCompletionsUtils;

@Component("AnthropicVertexCompletion")
public class AnthropicVertexAdaptor implements CompletionAdaptorDelegator<AnthropicVertexProperty> {

    private final Callbacks.SseEventConverter<StreamCompletionResponse> sseConverter = new Callbacks.DefaultSseConverter();

    private final Callbacks.ChannelErrorCallback<CompletionResponse> errorCallback = (errorResponse, response) -> {
        if(errorResponse != null && errorResponse.getError() != null) {
            errorResponse.getError().setHttpCode(response.code());
        }
    };

    @Autowired
    private com.ke.bella.openapi.protocol.message.AnthropicVertexAdaptor delegator;

    @Override
    public String getDescription() {
        return "Anthropic Vertex AI Chat Completions协议适配器";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AnthropicVertexProperty.class;
    }

    @Override
    public boolean supportsDirectPassthrough() {
        return false;
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, AnthropicVertexProperty property) {
        return completion(request, url, property, null);
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, AnthropicVertexProperty property,
            Callbacks.HttpDelegator httpDelegator) {
        if(httpDelegator != null) {
            return httpDelegator.request(request, CompletionResponse.class, errorCallback);
        }
        MessageRequest messageRequest = TransferToCompletionsUtils.convertRequest(request);
        clearLargeData(request);
        delegator.createMessages(messageRequest, url, property);
        return (CompletionResponse) EndpointContext.getProcessData().getResponse();
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, AnthropicVertexProperty property,
            Callbacks.StreamCompletionCallback callback) {
        streamCompletion(request, url, property, callback, null);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, AnthropicVertexProperty property,
            Callbacks.StreamCompletionCallback callback, Callbacks.StreamDelegator streamDelegator) {
        if(streamDelegator != null) {
            streamDelegator.request(request, new CompletionSseListener(callback, sseConverter));
            return;
        }
        MessageRequest messageRequest = TransferToCompletionsUtils.convertRequest(request);
        clearLargeData(request);
        delegator.streamMessages(messageRequest, url, property, callback);
    }

}
