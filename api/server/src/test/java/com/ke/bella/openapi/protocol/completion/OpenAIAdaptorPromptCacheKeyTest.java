package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.Callbacks;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenAIAdaptorPromptCacheKeyTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void missingPromptCacheKeyIsNotGeneratedFromAkCode() {
        EndpointContext.getProcessData().setAkCode("ak-default");
        CompletionRequest request = CompletionRequest.builder().build();
        OpenAIProperty property = new OpenAIProperty();
        Callbacks.HttpDelegator delegator = new Callbacks.HttpDelegator() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T request(Object req, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
                return (T) CompletionResponse.builder().build();
            }
        };

        new OpenAIAdaptor().completion(request, "http://localhost/chat", property, delegator);

        assertThat(request.getPrompt_cache_key()).isNull();
    }
}
