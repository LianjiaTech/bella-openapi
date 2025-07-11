package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.OpenAIProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("HuoshanMessage")
public class HuoshanAdaptor implements MessageDelegatorAdaptor<OpenAIProperty>  {
    @Autowired
    private com.ke.bella.openapi.protocol.completion.HuoshanAdaptor delegator;

    @Override
    public CompletionAdaptor<OpenAIProperty> delegator() {
        return delegator;
    }

    @Override
    public boolean isNativeSupport() {
        return false;
    }

    @Override
    public String getDescription() {
        return "Huoshan扩展的Openai协议模型服务适配/v1/message能力点";
    }
}
