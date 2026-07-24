package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component("KeCompletion")
public class KeAdaptor implements CompletionAdaptorDelegator<OpenAIProperty> {

    @Autowired
    private OpenAIAdaptor openAIAdaptor;

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property) {
        return completion(request, url, property, null);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.StreamCompletionCallback callback) {
        streamCompletion(request, url, property, callback, null);
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.HttpDelegator delegator) {
        normalizeThinkingOptions(request);
        request.setModel(property.getDeployName());
        return openAIAdaptor.completion(request, url, property, delegator);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.StreamCompletionCallback callback,
            Callbacks.StreamDelegator delegator) {
        normalizeThinkingOptions(request);
        request.setModel(property.getDeployName());
        openAIAdaptor.streamCompletion(request, url, property, callback, delegator);
    }

    @Override
    public String getDescription() {
        return "贝壳自部署协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return openAIAdaptor.getPropertyClass();
    }

    static void normalizeThinkingOptions(CompletionRequest request) {
        boolean enableThinking = isThinkingEnabled(request);
        request.setThinking(null);
        request.setReasoning_effort(null);
        request.setEnable_thinking(null);

        Map<String, Object> extraBody = request.getExtra_body();
        if(extraBody == null) {
            extraBody = new HashMap<>();
            request.setExtra_body(extraBody);
        }

        Map<String, Object> chatTemplateKwargs = new HashMap<>();
        Object existingChatTemplateKwargs = extraBody.get("chat_template_kwargs");
        if(existingChatTemplateKwargs instanceof Map) {
            ((Map<?, ?>) existingChatTemplateKwargs).forEach((key, value) -> chatTemplateKwargs.put(String.valueOf(key), value));
        }
        chatTemplateKwargs.put("enable_thinking", enableThinking);
        extraBody.put("chat_template_kwargs", chatTemplateKwargs);
    }

    private static boolean isThinkingEnabled(CompletionRequest request) {
        if(request.getEnable_thinking() != null) {
            return request.getEnable_thinking();
        }
        Object thinking = request.getThinking();
        if(thinking instanceof Map) {
            Object type = ((Map<?, ?>) thinking).get("type");
            return !"disabled".equals(type);
        }
        if(thinking != null) {
            return true;
        }
        return request.getReasoning_effort() != null;
    }
}
