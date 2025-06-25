package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("HuoshanCompletion")
public class HuoshanAdaptor implements CompletionAdaptorDelegator<OpenAIProperty> {
    @Autowired
    private OpenAIAdaptor openAIAdaptor;
    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.HttpDelegator delegator) {
        fillThinking(request);
        return openAIAdaptor.completion(request, url, property, delegator);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.StreamCompletionCallback callback,
            Callbacks.StreamDelegator delegator) {
        fillThinking(request);
        openAIAdaptor.streamCompletion(request, url, property, callback, delegator);
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property) {
        return completion(request, url, property, null);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.StreamCompletionCallback callback) {
        streamCompletion(request, url, property, callback, null);
    }

    @Override
    public String getDescription() {
        return "火山扩展OpenAI协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return OpenAIProperty.class;
    }

    private void fillThinking(CompletionRequest request) {
        if(request.getReasoning_effort() != null) {
            request.setThinking(ThinkingConfig.enable());
        } else {
            request.setThinking(ThinkingConfig.disable());
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    private static class ThinkingConfig {
        private String type;

        public static ThinkingConfig enable() {
            return new ThinkingConfig("enabled");
        }

        public static ThinkingConfig disable() {
            return new ThinkingConfig("disabled");
        }
    }
}
