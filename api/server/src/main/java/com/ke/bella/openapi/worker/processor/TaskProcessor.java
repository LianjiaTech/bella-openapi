package com.ke.bella.openapi.worker.processor;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.TaskWrapper;
import lombok.Builder;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Builder
@SuppressWarnings("all")
public class TaskProcessor {

    private final ChannelDB channel;
    @Singular
    private final List<EndpointTaskProcessor> processors;
    private Map<String, EndpointTaskProcessor> processorMap;

    private Map<String, EndpointTaskProcessor> getProcessorMap() {
        if(processorMap == null) {
            processorMap = processors.stream()
                    .collect(Collectors.toMap(EndpointTaskProcessor::endpoint, Function.identity()));
        }
        return processorMap;
    }

    public void executeTask(TaskWrapper taskWrapper, Runnable releaseSlot) {
        TaskSlot slot = new TaskSlot(releaseSlot);
        boolean asyncStarted = false;
        try {
            String endpoint = resolveEndpoint(taskWrapper);
            EndpointTaskProcessor processor = getProcessorMap().get(endpoint);
            if(processor == null) {
                throw new UnsupportedOperationException("No processor found for endpoint: " + endpoint);
            }
            TaskProcessResult result = processor.execute(taskWrapper, channel, slot);
            asyncStarted = result == TaskProcessResult.ASYNC_STARTED;
        } finally {
            EndpointContext.clearAll();
            if(!asyncStarted) {
                slot.release();
            }
        }
    }

    private String resolveEndpoint(TaskWrapper taskWrapper) {
        String endpoint = taskWrapper.getTask().getEndpoint();
        if(endpoint != null && !endpoint.isEmpty()) {
            return endpoint;
        }
        if(getProcessorMap().size() == 1) {
            return getProcessorMap().keySet().iterator().next();
        }
        throw new UnsupportedOperationException(
                "Cannot determine endpoint for task: " + taskWrapper.getTask().getTaskId()
                        + ", channel: " + channel.getChannelCode());
    }
}
