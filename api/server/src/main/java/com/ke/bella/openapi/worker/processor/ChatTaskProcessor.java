package com.ke.bella.openapi.worker.processor;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.callback.MergeReasoningCallback;
import com.ke.bella.openapi.protocol.completion.callback.SplitReasoningCallback;
import com.ke.bella.openapi.protocol.completion.callback.ToolCallSimulatorCallback;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.worker.WorkerStreamingCallback;
import com.ke.bella.queue.TaskWrapper;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Builder
@SuppressWarnings("all")
public class ChatTaskProcessor implements EndpointTaskProcessor {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    private final AdaptorManager adaptorManager;
    private final OpenapiClient openapiClient;
    private final ISafetyCheckService<SafetyCheckRequest.Chat> chatSafetyCheckService;

    @Override
    public String endpoint() {
        return CHAT_COMPLETIONS_ENDPOINT;
    }

    @Override
    public TaskProcessResult execute(TaskWrapper taskWrapper, ChannelDB channel, TaskSlot slot) {
        String taskId = taskWrapper.getTask().getTaskId();
        log.info("Text task started, taskId: {}, channel: {}", taskId, channel.getChannelCode());
        try {
            OpenapiResponse response = processCompletionRequest(taskWrapper.getTask().getData(), taskWrapper, channel, slot);
            if(response != null) {
                taskWrapper.markComplete(createResult(response, channel));
                log.info("Text task completed, taskId: {}, channel: {}", taskId, channel.getChannelCode());
                return TaskProcessResult.SYNC_DONE;
            }
            log.info("Text task submitted in stream mode, taskId: {}, channel: {}", taskId, channel.getChannelCode());
            return TaskProcessResult.ASYNC_STARTED;
        } catch (Exception e) {
            log.error("Text task execution failed, taskId: {}, channel: {}", taskId, channel.getChannelCode(), e);
            OpenapiResponse errorResponse = OpenapiResponse.errorResponse(
                    OpenapiResponse.OpenapiError.builder().httpCode(500).message(e.getMessage()).build());
            taskWrapper.markComplete(createResult(errorResponse, channel));
            return TaskProcessResult.SYNC_DONE;
        }
    }

    private CompletionResponse processCompletionRequest(Map<String, Object> requestData, TaskWrapper taskWrapper, ChannelDB channel, TaskSlot slot) {
        CompletionRequest request = JacksonUtils.deserialize(JacksonUtils.serialize(requestData), CompletionRequest.class);

        EndpointContext.setEndpointData(CHAT_COMPLETIONS_ENDPOINT, channel.getEntityCode(), request, request.getUser());
        EndpointContext.setEndpointData(channel);

        EndpointProcessData processData = EndpointContext.getProcessData();
        processData.setRequestId(taskWrapper.getTask().getTaskId());

        CompletionAdaptor adaptor = adaptorManager.getProtocolAdaptor(CHAT_COMPLETIONS_ENDPOINT, processData.getProtocol(), CompletionAdaptor.class);
        CompletionProperty property = (CompletionProperty) JacksonUtils.deserialize(channel.getChannelInfo(), adaptor.getPropertyClass());

        EndpointContext.setEncodingType(property.getEncodingType());
        if(request.isStream()) {
            ApikeyInfo apikeyInfo = openapiClient.whoami(taskWrapper.getTask().getAk());
            Callbacks.StreamCompletionCallbackNode root = new SplitReasoningCallback(property);
            root.addLast(new ToolCallSimulatorCallback(processData));
            root.addLast(new MergeReasoningCallback(property));
            root.addLast(new WorkerStreamingCallback(taskWrapper, processData, apikeyInfo, chatSafetyCheckService, slot));
            adaptor.streamCompletion(request, processData.getForwardUrl(), property, root);
            return null;
        }
        return adaptor.completion(request, processData.getForwardUrl(), property);
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
