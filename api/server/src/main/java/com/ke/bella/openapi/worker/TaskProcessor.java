package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
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

    public void executeTask(TaskWrapper taskWrapper) {
        try {
            OpenapiResponse response = processRequest(taskWrapper.getTask().getData());
            handleResponse(taskWrapper, response);
        } catch (Exception e) {
            log.error("Task execution failed for channel: {}", channel.getChannelCode(), e);
            try {
                taskWrapper.markComplete(createErrorResult(e.getMessage()));
            } catch (Exception ex) {
                log.error("Failed to mark task complete for channel: {}", channel.getChannelCode(), ex);
            }
        }
    }

    private void handleResponse(TaskWrapper taskWrapper, OpenapiResponse response) {
        int httpCode = Optional.ofNullable(response.getError())
                .map(error -> Optional.ofNullable(error.getHttpCode()).orElse(500))
                .orElse(200);

        if(httpCode == 200) {
            taskWrapper.markComplete(createSuccessResult(response));
        } else if(httpCode == 429 || httpCode == 503) {
            taskWrapper.markRetryLater();
        } else {
            String errorMsg = Optional.ofNullable(response.getError())
                    .map(OpenapiResponse.OpenapiError::getMessage)
                    .orElse("Unknown error");
            taskWrapper.markComplete(createErrorResult(errorMsg));
        }
    }

    private OpenapiResponse processRequest(Map<String, Object> requestData) {
        //todo:: support more entity types
        if(EntityConstants.MODEL.equals(channel.getEntityType())) {
            return processCompletionRequest(requestData);
        } else {
            throw new UnsupportedOperationException("Unsupported entity type: " + channel.getEntityType());
        }
    }

    private CompletionResponse processCompletionRequest(Map<String, Object> requestData) {
        CompletionRequest request = JacksonUtils.deserialize(JacksonUtils.serialize(requestData), CompletionRequest.class);

        EndpointContext.setEndpointData(CHAT_COMPLETIONS_ENDPOINT, channel.getEntityCode(), request);
        EndpointContext.setEndpointData(channel);

        EndpointProcessData processData = EndpointContext.getProcessData();
        CompletionAdaptor adaptor = adaptorManager.getProtocolAdaptor(CHAT_COMPLETIONS_ENDPOINT, processData.getProtocol(), CompletionAdaptor.class);
        CompletionProperty property = (CompletionProperty) JacksonUtils.deserialize(channel.getChannelInfo(), adaptor.getPropertyClass());

        EndpointContext.setEncodingType(property.getEncodingType());
        return adaptor.completion(request, processData.getForwardUrl(), property);
    }

    private Map<String, Object> createSuccessResult(OpenapiResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put("status_code", 200);
        result.put("body", response);
        return result;
    }

    private Map<String, Object> createErrorResult(String errorMessage) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", errorMessage);
        Map<String, Object> result = new HashMap<>();
        result.put("status_code", 500);
        result.put("body", errorBody);
        return result;
    }
}
