package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("GoogleCompletion")
public class GoogleAdaptor implements CompletionAdaptorDelegator<OpenAIProperty> {

    @Autowired
    private OpenAIAdaptor openAIAdaptor;

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.HttpDelegator delegator) {
        fillExtraBody(request);
        return openAIAdaptor.completion(request, url, property, delegator);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.StreamCompletionCallback callback,
            Callbacks.StreamDelegator delegator) {
        fillExtraBody(request);
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
        return "Google扩展OpenAI协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return openAIAdaptor.getPropertyClass();
    }

    private void fillExtraBody(CompletionRequest request) {
        if(request.getReasoning_effort() != null) {
            request.setExtra_body(new ExtraBody.ExtraBodyBuilder().google(new GoogleExtraBody(true)).build());
            request.setReasoning_effort(null);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    private static class ExtraBody {
        private Object google;
    }

    @Data
    private static class GoogleExtraBody {
        private GoogleThinkingConfig thinking_config;
        private String thought_tag_marker;

        public GoogleExtraBody(boolean isThinking) {
            if(isThinking) {
                this.thinking_config = new GoogleThinkingConfig(true);
                this.thought_tag_marker = "think";
            }
        }
    }

    @Data
    @AllArgsConstructor
    private static class GoogleThinkingConfig {
        private boolean include_thoughts;
    }

}
