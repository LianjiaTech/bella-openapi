package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("HuoshanCompletion")
public class HuoshanAdaptor implements CompletionAdaptor<OpenAIProperty> {
    @Autowired
    private OpenAIAdaptor openAIAdaptor;

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property) {
        fillThinking(request);
        return openAIAdaptor.completion(request, url, property);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.StreamCompletionCallback callback) {
        fillThinking(request);
        openAIAdaptor.streamCompletion(request, url, property, callback);
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
