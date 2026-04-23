package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.protocol.completion.callback.StreamCompletionCallback;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.queue.TaskWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class WorkerStreamingCallback extends StreamCompletionCallback {

    private static final ScheduledExecutorService TIMEOUT_SCHEDULER =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "stream-timeout");
                t.setDaemon(true);
                return t;
            });
    private static final long STREAM_TIMEOUT_MINUTES = 5;

    private final TaskWrapper taskWrapper;
    private final AtomicInteger seq = new AtomicInteger(0);
    private final AtomicBoolean released = new AtomicBoolean(false);
    private final Runnable releaseSlot;
    private final ScheduledFuture<?> timeoutFuture;

    public WorkerStreamingCallback(TaskWrapper taskWrapper, EndpointProcessData processData, ApikeyInfo apikeyInfo,
            ISafetyCheckService<SafetyCheckRequest.Chat> safetyService, Runnable releaseSlot) {
        super(null, processData, apikeyInfo, null, safetyService);
        this.taskWrapper = taskWrapper;
        this.releaseSlot = releaseSlot;
        this.timeoutFuture = TIMEOUT_SCHEDULER.schedule(() -> {
            if(released.compareAndSet(false, true)) {
                log.warn("Stream task timeout after {}min, auto releasing slot, taskId: {}",
                        STREAM_TIMEOUT_MINUTES, taskWrapper.getTask().getTaskId());
                releaseSlot.run();
            }
        }, STREAM_TIMEOUT_MINUTES, TimeUnit.MINUTES);
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

    @Override
    public void finish() {
        Map<String, Object> result = new HashMap<>();
        result.put("status_code", 200);
        result.put("stream", true);
        try {
            taskWrapper.markComplete(result);
        } finally {
            if(released.compareAndSet(false, true)) {
                releaseSlot.run();
                timeoutFuture.cancel(false);
            }
        }
        log.info("Stream task completed, taskId: {}", taskWrapper.getTask().getTaskId());
    }

    @Override
    public void finish(BellaException exception) {
        OpenapiResponse.OpenapiError openapiError = exception.convertToOpenapiError();
        Map<String, Object> result = new HashMap<>();
        result.put("status_code", Optional.ofNullable(openapiError.getHttpCode()).orElse(500));
        result.put("error", openapiError.getMessage());
        try {
            taskWrapper.markComplete(result);
        } finally {
            if(released.compareAndSet(false, true)) {
                releaseSlot.run();
                timeoutFuture.cancel(false);
            }
        }
        log.error("Stream task failed, taskId: {}, httpCode: {}, error: {}",
                taskWrapper.getTask().getTaskId(), openapiError.getHttpCode(), openapiError.getMessage());
    }

}
