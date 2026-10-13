package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.queue.TaskWrapper;

public class WorkerStreamingCallback extends AbstractWorkerStreamingCallback {

    public WorkerStreamingCallback(TaskWrapper taskWrapper, EndpointProcessData processData, ApikeyInfo apikeyInfo,
            ISafetyCheckService<SafetyCheckRequest.Chat> safetyService, Runnable releaseSlot) {
        super(taskWrapper, processData, apikeyInfo, safetyService, releaseSlot);
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        msg.setChannelCode(processData.getChannelCode());
        super.callback(msg);
    }

    @Override
    public void send(Object data) {
        taskWrapper.emitProgress(String.valueOf(seq.getAndIncrement()), "message", data);
    }
}
