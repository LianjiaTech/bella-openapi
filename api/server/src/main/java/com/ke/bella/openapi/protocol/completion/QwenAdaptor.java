package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("QwenCompletion")
public class QwenAdaptor implements CompletionAdaptor<OpenAIProperty> {

    @Autowired
    private OpenAIAdaptor openAIAdaptor;

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, OpenAIProperty property) {
        fillExtraBody(request);
        return openAIAdaptor.completion(request, url, property);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, OpenAIProperty property, Callbacks.StreamCompletionCallback callback) {
        fillExtraBody(request);
        openAIAdaptor.streamCompletion(request, url, property, callback);
    }

    @Override
    public String getDescription() {
        return "通义千问扩展OpenAI协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return openAIAdaptor.getPropertyClass();
    }

    private void fillExtraBody(CompletionRequest request) {
        if(request.getReasoning_effort() != null) {
            request.setEnable_thinking(true);
            request.setReasoning_effort(null);
        } else if(request.getEnable_thinking() == null) {
            request.setEnable_thinking(false);
        }
    }

}
