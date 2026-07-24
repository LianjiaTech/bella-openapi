package com.ke.bella.openapi.worker.processor;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.message.MessageAdaptor;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.worker.WorkerMessageStreamingCallback;
import com.ke.bella.queue.TaskWrapper;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Builder
@SuppressWarnings("all")
public class MessageTaskProcessor implements EndpointTaskProcessor {
    private static final String MESSAGES_ENDPOINT = "/v1/messages";

    private final AdaptorManager adaptorManager;
    private final OpenapiClient openapiClient;
    private final ISafetyCheckService<SafetyCheckRequest.Chat> chatSafetyCheckService;

    @Override
    public String endpoint() {
        return MESSAGES_ENDPOINT;
    }

    @Override
    public TaskProcessResult execute(TaskWrapper taskWrapper, ChannelDB channel, TaskSlot slot) {
        String taskId = taskWrapper.getTask().getTaskId();
        log.info("Message task started, taskId: {}, channel: {}", taskId, channel.getChannelCode());
        try {
            OpenapiResponse response = processMessageRequest(taskWrapper.getTask().getData(), taskWrapper, channel, slot);
            if(response != null) {
                taskWrapper.markComplete(createResult(response, channel));
                log.info("Message task completed, taskId: {}, channel: {}", taskId, channel.getChannelCode());
                return TaskProcessResult.SYNC_DONE;
            }
            log.info("Message task submitted in stream mode, taskId: {}, channel: {}", taskId, channel.getChannelCode());
            return TaskProcessResult.ASYNC_STARTED;
        } catch (Exception e) {
            log.error("Message task execution failed, taskId: {}, channel: {}", taskId, channel.getChannelCode(), e);
            OpenapiResponse errorResponse = OpenapiResponse.errorResponse(
                    OpenapiResponse.OpenapiError.builder().httpCode(500).message(e.getMessage()).build());
            taskWrapper.markComplete(createResult(errorResponse, channel));
            return TaskProcessResult.SYNC_DONE;
        }
    }

    private MessageResponse processMessageRequest(Map<String, Object> requestData, TaskWrapper taskWrapper, ChannelDB channel, TaskSlot slot) {
        MessageRequest request = JacksonUtils.deserialize(JacksonUtils.serialize(requestData), MessageRequest.class);

        EndpointContext.setEndpointData(MESSAGES_ENDPOINT, channel.getEntityCode(), request);
        EndpointContext.setEndpointData(channel);

        EndpointProcessData processData = EndpointContext.getProcessData();
        processData.setRequestId(taskWrapper.getTask().getTaskId());

        MessageAdaptor adaptor = adaptorManager.getProtocolAdaptor(MESSAGES_ENDPOINT, processData.getProtocol(), MessageAdaptor.class);
        CompletionProperty property = (CompletionProperty) JacksonUtils.deserialize(channel.getChannelInfo(), adaptor.getPropertyClass());

        EndpointContext.setEncodingType(property.getEncodingType());
        if(Boolean.TRUE.equals(request.getStream())) {
            ApikeyInfo apikeyInfo = openapiClient.whoami(taskWrapper.getTask().getAk());
            adaptor.streamMessages(request, processData.getForwardUrl(), property,
                    new WorkerMessageStreamingCallback(taskWrapper, processData, apikeyInfo, chatSafetyCheckService, slot));
            return null;
        }
        return adaptor.createMessages(request, processData.getForwardUrl(), property);
    }

    private Map<String, Object> createResult(OpenapiResponse response, ChannelDB channel) {
        int httpCode = Optional.ofNullable(response.getError())
                .map(error -> Optional.ofNullable(error.getHttpCode()).orElse(500))
                .orElse(200);
        response.setChannelCode(channel.getChannelCode());
        Map<String, Object> result = new HashMap<>();
        result.put("status_code", httpCode);
        result.put("body", response);
        return result;
    }
}
