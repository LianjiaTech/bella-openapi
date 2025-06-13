package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ToolCallSimulator;

public interface MessageAdaptor<T extends CompletionProperty> extends IProtocolAdaptor {

    CompletionAdaptor<T> delegator();

    default MessageResponse createMessages (MessageRequest request, String url, T property, EndpointProcessData processData) {
        CompletionAdaptor<T> delegator = decorateAdaptor(delegator(), property, processData);
        CompletionResponse completionResponse = delegator.completion(TransferUtils.convertRequest(request ,isNativeSupport()), url, property);
        processData.setResponse(completionResponse);
        return TransferUtils.convertResponse(completionResponse);
    }

    default void streamMessages(MessageRequest request, String url, T property, EndpointProcessData processData, Callbacks.StreamCompletionCallback callback) {
        CompletionAdaptor<T> delegator = decorateAdaptor(delegator(), property, processData);
        delegator.streamCompletion(TransferUtils.convertRequest(request, isNativeSupport()), url, property, callback);
    }

    @Override
    default String endpoint() {
        return "/v1/messages";
    }

    default CompletionAdaptor<T> decorateAdaptor(CompletionAdaptor<T> adaptor, CompletionProperty property, EndpointProcessData processData) {
        if(property.isFunctionCallSimulate()) {
            adaptor = new ToolCallSimulator<>(adaptor, processData);
        }
        return adaptor;
    }

    @Override
    default Class<?> getPropertyClass() {
        return delegator().getPropertyClass();
    }

    boolean isNativeSupport();
}
