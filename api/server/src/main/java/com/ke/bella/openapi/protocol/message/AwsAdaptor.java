package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.protocol.completion.AwsProperty;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("AwsMessage")
public class AwsAdaptor implements MessageDelegatorAdaptor<AwsProperty> {
    @Autowired
    private com.ke.bella.openapi.protocol.completion.AwsAdaptor delegator;

    @Autowired
    private AnthropicAdaptor anthropicAdaptor;

    @Override
    public CompletionAdaptor<AwsProperty> delegator() {
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
        return "Aws协议模型服务适配/v1/message能力点";
    }
}
