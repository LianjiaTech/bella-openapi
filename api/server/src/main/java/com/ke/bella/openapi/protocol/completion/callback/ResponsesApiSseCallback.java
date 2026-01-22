package com.ke.bella.openapi.protocol.completion.callback;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiStreamEvent;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResponsesApiSseCallback implements Callbacks.ResponsesApiSseCallback {
    protected final SseEmitter sse;
    protected final EndpointProcessData processData;
    protected final ApikeyInfo apikeyInfo;
    protected final EndpointLogger logger;
    protected Long firstPackageTime;
    protected ResponsesApiResponse.Usage usage;
    protected String responseId;
    protected boolean completed = false;

    public ResponsesApiSseCallback(SseEmitter sse, EndpointProcessData processData,
            ApikeyInfo apikeyInfo, EndpointLogger logger) {
        this.sse = sse;
        this.processData = processData;
        this.apikeyInfo = apikeyInfo;
        this.logger = logger;
    }

    @Override
    public void onEvent(String eventId, String eventType, String eventData) {
        if(completed) {
            return;
        }

        if(firstPackageTime == null && isFirstDataEvent(eventType)) {
            firstPackageTime = DateTimeUtils.getCurrentMills();
            log.debug("First package received at: {}, eventType: {}", firstPackageTime, eventType);
        }

        if("response.completed".equals(eventType)) {
            extractUsageAndMetadata(eventData);
        }

        sendSseEvent(eventType, eventData);
    }

    @Override
    public void onComplete() {
        if(completed) {
            return;
        }
        completed = true;

        if(sse != null) {
            sse.complete();
        }
        log();
    }

    @Override
    public void onError(ChannelException exception) {
        if(completed) {
            return;
        }
        completed = true;

        log.error("Responses API SSE error: {}", exception.getMessage(), exception);

        if(sse != null) {
            try {
                ResponsesApiResponse errorResponse = new ResponsesApiResponse();
                errorResponse.setError(exception.convertToOpenapiError());

                SseEmitter.SseEventBuilder errorEvent = SseEmitter.event()
                        .name(" " + "response.error")
                        .data(" " + JacksonUtils.serialize(errorResponse));
                sse.send(errorEvent);
                sse.completeWithError(exception);
            } catch (IOException e) {
                log.error("Failed to send error event", e);
            }
        }
        log();
    }

    private boolean isFirstDataEvent(String eventType) {
        return "response.created".equals(eventType);
    }

    private void extractUsageAndMetadata(String eventData) {
        try {
            // TODO: Remove debug logs after usage issue is resolved
            log.info("[DEBUG-USAGE] Extracting usage from response.completed event, eventData length: {}",
                    eventData != null ? eventData.length() : 0);

            ResponsesApiStreamEvent event = JacksonUtils.deserialize(eventData, ResponsesApiStreamEvent.class);
            if(event == null) {
                log.info("[DEBUG-USAGE] Failed to parse ResponsesApiStreamEvent from eventData");
                return;
            }

            if(event.getResponse() == null) {
                log.info("[DEBUG-USAGE] ResponsesApiStreamEvent.response is null");
                return;
            }

            ResponsesApiResponse response = event.getResponse();

            if(StringUtils.isNotBlank(response.getId())) {
                this.responseId = response.getId();
                log.info("[DEBUG-USAGE] Extracted response_id: {}", this.responseId);
            }

            if(response.getUsage() != null) {
                this.usage = response.getUsage();
                log.info("[DEBUG-USAGE] Extracted usage from response.completed: input={}, output={}, total={}",
                        usage.getInput_tokens(), usage.getOutput_tokens(), usage.getTotal_tokens());
            } else {
                log.info("[DEBUG-USAGE] Usage is null in response.completed event");
            }
        } catch (Exception e) {
            log.error("[DEBUG-USAGE] Failed to extract usage from response.completed event, eventData: {}", eventData, e);
        }
    }

    private void sendSseEvent(String eventType, String eventData) {
        if(sse == null) {
            return;
        }

        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event();
            if(StringUtils.isNotBlank(eventType)) {
                event.name(" " + eventType);
            }
            event.data(" " + eventData);
            sse.send(event);
        } catch (IOException e) {
            log.error("Failed to send SSE event: {}", eventType, e);
            throw new RuntimeException("Failed to send SSE event", e);
        }
    }

    private void log() {
        long endTime = DateTimeUtils.getCurrentSeconds();
        processData.setDuration(endTime - processData.getRequestTime());
        processData.setFirstPackageTime(firstPackageTime == null ? DateTimeUtils.getCurrentMills() : firstPackageTime);

        // TODO: Remove debug logs after usage issue is resolved
        if(usage != null) {
            // Build response object for ResponsesLogHandler
            ResponsesApiResponse response = ResponsesApiResponse.builder()
                    .id(responseId)
                    .created(endTime)
                    .usage(usage)
                    .build();
            processData.setResponse(response);

            log.info("[DEBUG-USAGE] Setting response with usage to processData: input={}, output={}, total={}",
                    usage.getInput_tokens(), usage.getOutput_tokens(), usage.getTotal_tokens());
        } else {
            log.info("[DEBUG-USAGE] Usage is null in log() method, streaming request may not have received response.completed event");
        }

        if(StringUtils.isNotBlank(responseId)) {
            processData.setChannelRequestId(responseId);
        }

        logger.log(processData);
    }
}
