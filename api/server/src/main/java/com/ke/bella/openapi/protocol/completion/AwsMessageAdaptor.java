package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.TransferToCompletionsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component("AwsMessageCompletion")
public class AwsMessageAdaptor implements CompletionAdaptor<AwsMessageProperty> {

    @Autowired
    private com.ke.bella.openapi.protocol.message.AwsMessageAdaptor delegator;

    @Override
    public String getDescription() {
        return "亚马逊Message API协议版本";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AwsMessageProperty.class;
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, AwsMessageProperty property) {
        MessageRequest messageRequest = TransferToCompletionsUtils.convertRequest(request);
        request.clearLargeData();
        delegator.createMessages(messageRequest, url, property);
        return (CompletionResponse) EndpointContext.getProcessData().getResponse();
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, AwsMessageProperty property, Callbacks.StreamCompletionCallback callback) {
        MessageRequest messageRequest = TransferToCompletionsUtils.convertRequest(request);
        request.clearLargeData();
        delegator.streamMessages(messageRequest, url, property, callback);
    }
}
