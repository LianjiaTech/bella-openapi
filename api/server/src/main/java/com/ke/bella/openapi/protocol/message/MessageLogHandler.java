package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.completion.CompletionLogHandler;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MessageLogHandler implements EndpointLogHandler {

    @Autowired
    private CompletionLogHandler completionLogHandler;

    @Override
    public void process(EndpointProcessData endpointProcessData) {
        completionLogHandler.process(endpointProcessData);
    }

    @Override
    public String endpoint() {
        return "/v1/messages";
    }
}
