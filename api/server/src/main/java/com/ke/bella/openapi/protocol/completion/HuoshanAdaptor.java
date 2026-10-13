package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("HuoshanCompletion")
public class HuoshanAdaptor implements CompletionAdaptor<HuoshanProperty> {
    @Autowired
    private OpenAIAdaptor openAIAdaptor;

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, HuoshanProperty property) {
        preprocess(request, property);
        return openAIAdaptor.completion(request, url, property);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, HuoshanProperty property, Callbacks.StreamCompletionCallback callback) {
        preprocess(request, property);
        openAIAdaptor.streamCompletion(request, url, property, callback);
    }

    @Override
    public String getDescription() {
        return "火山扩展OpenAI协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return HuoshanProperty.class;
    }

    private void preprocess(CompletionRequest request, HuoshanProperty property) {
        fillThinking(request);
        convertDeveloperToSystem(request, property);
    }

    private void convertDeveloperToSystem(CompletionRequest request, HuoshanProperty property) {
        if(!property.isConvertDeveloperToSystem() || request.getMessages() == null) {
            return;
        }
        request.getMessages().stream()
                .filter(message -> "developer".equals(message.getRole()))
                .forEach(message -> message.setRole("system"));
    }

    private void fillThinking(CompletionRequest request) {
        if(request.getReasoning_effort() != null) {
            request.setThinking(ThinkingConfig.enable());
        } else {
            if(request.getThinking() == null) {
                request.setThinking(ThinkingConfig.disable());
            }
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
