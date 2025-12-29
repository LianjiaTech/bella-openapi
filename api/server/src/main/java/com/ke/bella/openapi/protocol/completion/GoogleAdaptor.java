package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("GoogleCompletion")
public class GoogleAdaptor implements CompletionAdaptor<OpenAIProperty> {

    @Autowired
    private OpenAIAdaptor openAIAdaptor;

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property) {
        fillExtraBody(request);
        CompletionResponse response = openAIAdaptor.completion(request, url, property);
        normalizeGoogleUsage(response);
        return response;
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.StreamCompletionCallback callback) {
        fillExtraBody(request);
        Callbacks.StreamCompletionCallbackNode wrappedCallback = new Callbacks.StreamCompletionCallbackNode() {
            @Override
            public StreamCompletionResponse doCallback(StreamCompletionResponse msg) {
                normalizeGoogleUsage(msg);
                return msg;
            }
        };
        wrappedCallback.addLast(callback);
        openAIAdaptor.streamCompletion(request, url, property, wrappedCallback);
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
            request.setRealExtraBody(new ExtraBody.ExtraBodyBuilder().google(new GoogleExtraBody(true)).build());
            request.setReasoning_effort(null);
        }
    }

    private void normalizeGoogleUsage(CompletionResponse response) {
        if (response == null || response.getUsage() == null) {
            return;
        }
        CompletionResponse.TokenUsage usage = response.getUsage();
        CompletionResponse.TokensDetail details = usage.getCompletion_tokens_details();
        if (details != null) {
            int detailsTotal = details.getReasoning_tokens() + details.getAudio_tokens() + details.getImage_tokens();
            if (detailsTotal > 0) {
                usage.setCompletion_tokens(usage.getCompletion_tokens() + detailsTotal);
                usage.setTotal_tokens(usage.getPrompt_tokens() + usage.getCompletion_tokens());
            }
        }
    }

    private void normalizeGoogleUsage(StreamCompletionResponse response) {
        if (response == null || response.getUsage() == null) {
            return;
        }
        CompletionResponse.TokenUsage usage = response.getUsage();
        CompletionResponse.TokensDetail details = usage.getCompletion_tokens_details();
        if (details != null) {
            int detailsTotal = details.getReasoning_tokens() + details.getAudio_tokens() + details.getImage_tokens();
            if (detailsTotal > 0) {
                usage.setCompletion_tokens(usage.getCompletion_tokens() + detailsTotal);
                usage.setTotal_tokens(usage.getPrompt_tokens() + usage.getCompletion_tokens());
            }
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
