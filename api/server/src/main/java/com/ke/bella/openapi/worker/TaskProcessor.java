package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.common.EntityConstants;
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
import com.ke.bella.queue.TaskWrapper;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Builder
@SuppressWarnings("all")
public class TaskProcessor {
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    private final ChannelDB channel;
    private final AdaptorManager adaptorManager;
    private final OpenapiClient openapiClient;
    private final ISafetyCheckService<SafetyCheckRequest.Chat> chatSafetyCheckService;

    public void executeTask(TaskWrapper taskWrapper) {
        try {
            OpenapiResponse response = processRequest(taskWrapper.getTask().getData(), taskWrapper);
            if(response != null) {
                taskWrapper.markComplete(createResult(response));
            }
        } catch (Exception e) {
            log.error("Task execution failed for channel: {}", channel.getChannelCode(), e);
            OpenapiResponse errorResponse = OpenapiResponse.errorResponse(
                    OpenapiResponse.OpenapiError.builder().httpCode(500).message(e.getMessage()).build());
            taskWrapper.markComplete(createResult(errorResponse));
        } finally {
            EndpointContext.clearAll();
        }
    }

    private OpenapiResponse processRequest(Map<String, Object> requestData, TaskWrapper taskWrapper) {
        // todo:: support more entity types
        if(EntityConstants.MODEL.equals(channel.getEntityType())) {
            return processCompletionRequest(requestData, taskWrapper);
        } else {
            throw new UnsupportedOperationException("Unsupported entity type: " + channel.getEntityType());
        }
    }

    private CompletionResponse processCompletionRequest(Map<String, Object> requestData, TaskWrapper taskWrapper) {
        CompletionRequest request = JacksonUtils.deserialize(JacksonUtils.serialize(requestData), CompletionRequest.class);

        EndpointContext.setEndpointData(CHAT_COMPLETIONS_ENDPOINT, channel.getEntityCode(), request);
        EndpointContext.setEndpointData(channel);

        EndpointProcessData processData = EndpointContext.getProcessData();
        CompletionAdaptor adaptor = adaptorManager.getProtocolAdaptor(CHAT_COMPLETIONS_ENDPOINT, processData.getProtocol(), CompletionAdaptor.class);
        CompletionProperty property = (CompletionProperty) JacksonUtils.deserialize(channel.getChannelInfo(), adaptor.getPropertyClass());

        EndpointContext.setEncodingType(property.getEncodingType());
        if(request.isStream()) {
            ApikeyInfo apikeyInfo = openapiClient.whoami(taskWrapper.getTask().getAk());
            Callbacks.StreamCompletionCallbackNode root = new SplitReasoningCallback(property);
            root.addLast(new ToolCallSimulatorCallback(processData));
            root.addLast(new MergeReasoningCallback(property));
            root.addLast(new WorkerStreamingCallback(taskWrapper, processData, apikeyInfo, chatSafetyCheckService));
            adaptor.streamCompletion(request, processData.getForwardUrl(), property, root);
            return null;
        }
        return adaptor.completion(request, processData.getForwardUrl(), property);
    }

    private Map<String, Object> createResult(OpenapiResponse response) {
        int httpCode = Optional.ofNullable(response.getError())
                .map(error -> Optional.ofNullable(error.getHttpCode()).orElse(500))
                .orElse(200);
        Map<String, Object> result = new HashMap<>();
        result.put("status_code", httpCode);
        result.put("body", response);
        return result;
    }
}
