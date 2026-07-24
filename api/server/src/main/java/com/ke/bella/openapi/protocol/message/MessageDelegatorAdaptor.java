package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.AnthropicProperty;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ToolCallSimulator;
import org.apache.commons.lang3.StringUtils;

public interface MessageDelegatorAdaptor<T extends CompletionProperty> extends MessageDelegator<T> {

    CompletionAdaptor<T> delegator();

    AnthropicAdaptor anthropicAdaptor();

    default AnthropicProperty buildAnthropicProperty(T property) {
        AnthropicProperty anthropicProperty = new AnthropicProperty();
        anthropicProperty.setAuth(property.getAuth());
        anthropicProperty.setAnthropicVersion(
                property.getAnthropicVersion() != null
                        ? property.getAnthropicVersion()
                        : "2023-06-01");
        anthropicProperty.setDefaultMaxToken(property.getDefaultMaxToken());
        anthropicProperty.setExtraHeaders(property.getExtraHeaders());
        anthropicProperty.setEncodingType(property.getEncodingType());
        anthropicProperty.setMergeReasoningContent(property.isMergeReasoningContent());
        anthropicProperty.setSplitReasoningFromContent(property.isSplitReasoningFromContent());
        anthropicProperty.setFunctionCallSimulate(property.isFunctionCallSimulate());
        anthropicProperty.setQueueName(property.getQueueName());
        anthropicProperty.setDeployName(property.getDeployName());
        return anthropicProperty;
    }

    @Override
    default MessageResponse createMessages(MessageRequest request, String url, T property) {
        return createMessages(request, url, property, null);
    }

    @Override
    default MessageResponse createMessages(MessageRequest request, String url, T property, Callbacks.HttpDelegator httpDelegator) {
        // 检查是否启用 Anthropic 原生代理
        if(StringUtils.isNotBlank(property.getMessageEndpointUrl())) {
            AnthropicAdaptor adaptor = anthropicAdaptor();
            if(adaptor == null) {
                throw new IllegalStateException("AnthropicAdaptor not injected for native proxy");
            }
            AnthropicProperty anthropicProperty = buildAnthropicProperty(property);
            String proxyUrl = property.getMessageEndpointUrl();
            return adaptor.createMessages(request, proxyUrl, anthropicProperty, httpDelegator);
        }

        // 原有的委托逻辑
        CompletionAdaptor<T> delegator = decorateAdaptor(delegator(), property, EndpointContext.getProcessData());
        return createMessagesWithCompletionAdaptor(request, url, property, delegator);
    }

    default MessageResponse createMessagesWithCompletionAdaptor(MessageRequest request, String url, T property, CompletionAdaptor<T> delegator) {
        CompletionRequest completionRequest = TransferFromCompletionsUtils.convertRequest(request, isNativeSupport());
        clearLargeData(request);
        CompletionResponse completionResponse = delegator.completion(completionRequest, url, property);
        EndpointContext.getProcessData().setResponse(completionResponse);
        return TransferFromCompletionsUtils.convertResponse(completionResponse, request.getModel());
    }

    @Override
    default void streamMessages(MessageRequest request, String url, T property, Callbacks.StreamCompletionCallback callback) {
        streamMessages(request, url, property, callback, null);
    }

    @Override
    default void streamMessages(MessageRequest request, String url, T property, Callbacks.StreamCompletionCallback callback,
            Callbacks.StreamDelegator streamDelegator) {
        // 检查是否启用 Anthropic 原生代理
        if(StringUtils.isNotBlank(property.getMessageEndpointUrl())) {
            AnthropicAdaptor adaptor = anthropicAdaptor();
            if(adaptor == null) {
                throw new IllegalStateException("AnthropicAdaptor not injected for native proxy");
            }
            AnthropicProperty anthropicProperty = buildAnthropicProperty(property);
            String proxyUrl = property.getMessageEndpointUrl();
            adaptor.streamMessages(request, proxyUrl, anthropicProperty, callback, streamDelegator);
            return;
        }

        // 原有的委托逻辑
        CompletionAdaptor<T> delegator = decorateAdaptor(delegator(), property, EndpointContext.getProcessData());
        streamMessagesWithCompletionAdaptor(request, url, property, callback, delegator);
    }

    default void streamMessagesWithCompletionAdaptor(MessageRequest request, String url, T property, Callbacks.StreamCompletionCallback callback,
            CompletionAdaptor<T> delegator) {
        CompletionRequest completionRequest = TransferFromCompletionsUtils.convertRequest(request, isNativeSupport());
        clearLargeData(request);
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
