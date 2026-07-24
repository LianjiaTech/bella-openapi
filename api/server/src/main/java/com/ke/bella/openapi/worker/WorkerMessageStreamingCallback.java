package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.protocol.message.StreamMessageResponse;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.queue.TaskWrapper;

public class WorkerMessageStreamingCallback extends AbstractWorkerStreamingCallback {

    public WorkerMessageStreamingCallback(TaskWrapper taskWrapper, EndpointProcessData processData, ApikeyInfo apikeyInfo,
            ISafetyCheckService<SafetyCheckRequest.Chat> safetyService, Runnable releaseSlot) {
        super(taskWrapper, processData, apikeyInfo, safetyService, releaseSlot);
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        msg.setChannelCode(processData.getChannelCode());
        if(processData.isNativeSend()) {
            return;
        }
        taskWrapper.emitProgress(String.valueOf(seq.getAndIncrement()), "message", msg);
    }

    @Override
    public void send(Object data) {
        if(data instanceof StreamMessageResponse) {
            StreamMessageResponse response = (StreamMessageResponse) data;
            response.setChannelCode(processData.getChannelCode());
            taskWrapper.emitProgress(String.valueOf(seq.getAndIncrement()), response.getType(), response);
            return;
        }
        taskWrapper.emitProgress(String.valueOf(seq.getAndIncrement()), "message", data);
    }

    @Override
    protected Object buildErrorProgress(OpenapiResponse.OpenapiError openapiError) {
        StreamMessageResponse response = StreamMessageResponse.error(openapiError.getType(), openapiError.getMessage(), openapiError.getHttpCode());
        response.setChannelCode(processData.getChannelCode());
        return response;
    }

    @Override
    public void done() {

    }
}
