package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.VertexProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("VertexMessage")
public class VertexAdaptor implements MessageDelegatorAdaptor<VertexProperty> {
    @Autowired
    private com.ke.bella.openapi.protocol.completion.VertexAdaptor delegator;

    @Override
    public CompletionAdaptor<VertexProperty> delegator() {
        return delegator;
    }

    @Override
    public boolean isNativeSupport() {
        return true;  // Vertex natively supports Messages API format
    }

    @Override
    public String getDescription() {
        return "Google Vertex AI (Gemini) Message API协议适配";
    }
}
