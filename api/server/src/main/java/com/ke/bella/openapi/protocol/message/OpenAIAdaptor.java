package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.OpenAIProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("OpenAIMessage")
public class OpenAIAdaptor implements MessageDelegatorAdaptor<OpenAIProperty> {
    @Autowired
    private com.ke.bella.openapi.protocol.completion.OpenAIAdaptor delegator;

    @Autowired
    private AnthropicAdaptor anthropicAdaptor;

    @Override
    public CompletionAdaptor<OpenAIProperty> delegator() {
        return delegator;
    }

    @Override
    public AnthropicAdaptor anthropicAdaptor() {
        return anthropicAdaptor;
    }

    @Override
    public boolean isNativeSupport() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Openai协议模型服务适配/v1/message能力点";
    }
}
