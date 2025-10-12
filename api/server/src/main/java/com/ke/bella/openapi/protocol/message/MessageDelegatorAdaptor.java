package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ToolCallSimulator;

public interface MessageDelegatorAdaptor<T extends CompletionProperty> extends MessageAdaptor<T> {

    CompletionAdaptor<T> delegator();


    @Override
    default MessageResponse createMessages (MessageRequest request, String url, T property) {
        CompletionAdaptor<T> delegator = decorateAdaptor(delegator(), property, EndpointContext.getProcessData());
        CompletionRequest completionRequest = TransferFromCompletionsUtils.convertRequest(request ,isNativeSupport());
        request.clearLargeData();
        CompletionResponse completionResponse = delegator.completion(completionRequest, url, property);
        EndpointContext.getProcessData().setResponse(completionResponse);
        return TransferFromCompletionsUtils.convertResponse(completionResponse);
    }

    @Override
    default void streamMessages(MessageRequest request, String url, T property, Callbacks.StreamCompletionCallback callback) {
        CompletionAdaptor<T> delegator = decorateAdaptor(delegator(), property, EndpointContext.getProcessData());
        CompletionRequest completionRequest =  TransferFromCompletionsUtils.convertRequest(request, isNativeSupport());
        request.clearLargeData();
        delegator.streamCompletion(completionRequest, url, property, callback);
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

    @Override
    default String endpoint() {
        return "/v1/messages";
    }

    boolean isNativeSupport();
}
