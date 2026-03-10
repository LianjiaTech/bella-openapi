package com.ke.bella.openapi.protocol.tts;

import java.util.HashMap;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.DateTimeUtils;

public class OpenAIStreamTtsCallback implements Callbacks.HttpStreamTtsCallback {

    final Sender byteSender;
    final EndpointProcessData processData;
    final EndpointLogger logger;

    boolean first = true;
    long startTime = DateTimeUtils.getCurrentMills();

    public OpenAIStreamTtsCallback(Sender byteSender, EndpointProcessData processData, EndpointLogger logger) {
        this.byteSender = byteSender;
        this.processData = processData;
        this.logger = logger;
        processData.setMetrics(new HashMap<>());
    }

    @Override
    public void onOpen() {

    }

    @Override
    public void callback(byte[] msg) {
        byteSender.send(msg);
        if(first) {
            processData.getMetrics().put("ttft", DateTimeUtils.getCurrentMills() - startTime);
            first = false;
        }
    }

    @Override
    public void finish() {
        processData.getMetrics().put("ttlt", DateTimeUtils.getCurrentMills() - startTime);
        byteSender.close();
        logger.log(processData);
    }

    @Override
    public void finish(BellaException exception) {
        processData.setResponse(OpenapiResponse.errorResponse(exception.convertToOpenapiError()));
        finish();
    }
}
