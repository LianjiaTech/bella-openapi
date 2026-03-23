package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.callback.StreamCompletionCallback;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.queue.TaskWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class WorkerStreamingCallback extends StreamCompletionCallback {

    private final TaskWrapper taskWrapper;
    private final AtomicInteger seq = new AtomicInteger(0);

    public WorkerStreamingCallback(TaskWrapper taskWrapper, EndpointProcessData processData, ApikeyInfo apikeyInfo,
            ISafetyCheckService<SafetyCheckRequest.Chat> safetyService) {
        super(null, processData, apikeyInfo, null, safetyService);
        this.taskWrapper = taskWrapper;
    }

    @Override
    public void send(Object data) {
        taskWrapper.emitProgress(
                String.valueOf(seq.getAndIncrement()),
                "message",
                data
        );
    }

    @Override
    public void finish() {
        Map<String, Object> result = new HashMap<>();
        result.put("status_code", 200);
        result.put("stream", true);
        taskWrapper.markComplete(result);
        log.info("Stream task completed, taskId: {}", taskWrapper.getTask().getTaskId());
    }

    @Override
    public void finish(BellaException exception) {
        OpenapiResponse.OpenapiError openapiError = exception.convertToOpenapiError();
        Map<String, Object> result = new HashMap<>();
        result.put("status_code", Optional.ofNullable(openapiError.getHttpCode()).orElse(500));
        result.put("error", openapiError.getMessage());
        taskWrapper.markComplete(result);
        log.error("Stream task failed, taskId: {}, httpCode: {}, error: {}",
                taskWrapper.getTask().getTaskId(), openapiError.getHttpCode(), openapiError.getMessage());
    }

}
