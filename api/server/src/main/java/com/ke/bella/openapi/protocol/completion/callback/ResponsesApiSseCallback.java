package com.ke.bella.openapi.protocol.completion.callback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiStreamEvent;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.safety.StreamSafetyChecker;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResponsesApiSseCallback implements Callbacks.ResponsesApiSseCallback {
    protected final SseEmitter sse;
    protected final EndpointProcessData processData;
    protected final ApikeyInfo apikeyInfo;
    protected final EndpointLogger logger;
    protected final StreamSafetyChecker safetyChecker;
    protected Long firstPackageTime;
    protected ResponsesApiResponse.Usage usage;
    protected String responseId;
    protected ResponsesApiResponse terminalResponse;
    protected final List<ResponsesApiResponse.OutputItem> outputItems = new ArrayList<>();
    protected boolean completed = false;

    public ResponsesApiSseCallback(SseEmitter sse, EndpointProcessData processData,
            ApikeyInfo apikeyInfo, EndpointLogger logger,
            ISafetyCheckService<SafetyCheckRequest.Chat> safetyService) {
        this.sse = sse;
        this.processData = processData;
        this.apikeyInfo = apikeyInfo;
        this.logger = logger;
        this.safetyChecker = new StreamSafetyChecker(safetyService, processData.isMock());
    }

    @Override
    public void onEvent(String eventId, String eventType, String eventData) {
        if(completed || safetyChecker.isBlocked()) {
            return;
        }

        if(firstPackageTime == null && StringUtils.isNotBlank(eventData)) {
            firstPackageTime = DateTimeUtils.getCurrentMills();
            log.debug("First package received at: {}, eventType: {}", firstPackageTime, eventType);
        }

        // 积累输出文本
        if("response.output_text.delta".equals(eventType)) {
            accumulateText(eventData);
        }
        if("response.output_item.added".equals(eventType) || "response.output_item.done".equals(eventType)) {
            accumulateOutputItem(eventData);
        }
        if("response.function_call_arguments.delta".equals(eventType)) {
            accumulateFunctionCallArguments(eventData);
        }
        if("response.reasoning_summary_text.delta".equals(eventType)) {
            accumulateReasoningSummary(eventData);
        }

        // response.completed 事件需要先做安全检查再转发
        if("response.completed".equals(eventType)) {
            extractTerminalResponse(eventData);
            try {
                safetyChecker.check(true, this::buildSafetyCheckRequest);
            } catch (BellaException.SafetyCheckException e) {
                handleBlock(e);
                return;
            }
            sendSseEvent(eventType, eventData);
            return;
        }

        if(isFailureTerminalEvent(eventType)) {
            extractTerminalResponse(eventData);
            sendSseEvent(eventType, eventData);
            return;
        }

        // response.created 事件提取 responseId
        if("response.created".equals(eventType)) {
            extractResponseId(eventData);
        }

        // 其他事件先转发再检查
        sendSseEvent(eventType, eventData);

        // delta 事件做增量安全检查
        if("response.output_text.delta".equals(eventType)) {
            try {
                safetyChecker.check(false, this::buildSafetyCheckRequest);
            } catch (BellaException.SafetyCheckException e) {
                handleBlock(e);
            }
        }
    }

    @Override
    public void onComplete() {
        if(completed || safetyChecker.isBlocked()) {
            return;
        }
        completed = true;

        if(sse != null) {
            sse.complete();
        }
        log();
    }

    @Override
    public void onError(BellaException exception) {
        if(completed || safetyChecker.isBlocked()) {
            return;
        }
        completed = true;

        log.error("Responses API SSE error: {}", exception.getMessage(), exception);

        ResponsesApiResponse errorResponse = terminalResponse == null ? new ResponsesApiResponse() : terminalResponse;
        errorResponse.setId(StringUtils.defaultIfBlank(errorResponse.getId(), responseId));
        errorResponse.setStatus("failed");
        errorResponse.setError(exception.convertToOpenapiError());
        fillStructuredOutput(errorResponse);
        fillBufferedOutputText(errorResponse);
        terminalResponse = errorResponse;

        if(sse != null) {
            try {
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

    private void accumulateText(String eventData) {
        try {
            ResponsesApiStreamEvent event = JacksonUtils.deserialize(eventData, ResponsesApiStreamEvent.class);
            if(event != null && event.getDelta() != null) {
                safetyChecker.accumulate(event.getDelta());
                accumulateOutputText(event);
            }
        } catch (Exception e) {
            log.warn("Failed to parse delta from event data", e);
        }
    }

    private void accumulateOutputText(ResponsesApiStreamEvent event) {
        ResponsesApiResponse.OutputItem item = getOrCreateOutputItem(event, "message");
        item.setRole(StringUtils.defaultIfBlank(item.getRole(), "assistant"));
        List<ResponsesApiResponse.ContentItem> contentItems = item.getContent() == null
                ? new ArrayList<>()
                : new ArrayList<>(item.getContent());
        int contentIndex = event.getContent_index() == null || event.getContent_index() < 0 ? 0 : event.getContent_index();
        while(contentItems.size() <= contentIndex) {
            contentItems.add(null);
        }
        ResponsesApiResponse.ContentItem content = contentItems.get(contentIndex);
        if(content == null) {
            content = new ResponsesApiResponse.ContentItem();
            content.setType("output_text");
            contentItems.set(contentIndex, content);
        }
        content.setText(StringUtils.defaultString(content.getText()) + event.getDelta());
        item.setContent(contentItems);
    }

    private void accumulateFunctionCallArguments(String eventData) {
        try {
            ResponsesApiStreamEvent event = JacksonUtils.deserialize(eventData, ResponsesApiStreamEvent.class);
            if(event == null || event.getDelta() == null) {
                return;
            }
            ResponsesApiResponse.OutputItem item = getOrCreateOutputItem(event, "function_call");
            item.setArguments(StringUtils.defaultString(item.getArguments()) + event.getDelta());
        } catch (Exception e) {
            log.warn("Failed to parse function call arguments from event data", e);
        }
    }

    private void accumulateReasoningSummary(String eventData) {
        try {
            ResponsesApiStreamEvent event = JacksonUtils.deserialize(eventData, ResponsesApiStreamEvent.class);
            if(event == null || event.getDelta() == null) {
                return;
            }
            ResponsesApiResponse.OutputItem item = getOrCreateOutputItem(event, "reasoning");
            List<ResponsesApiResponse.SummaryItem> summaries = item.getSummary() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(item.getSummary());
            int summaryIndex = event.getSummary_index() == null || event.getSummary_index() < 0 ? 0 : event.getSummary_index();
            while(summaries.size() <= summaryIndex) {
                summaries.add(null);
            }
            ResponsesApiResponse.SummaryItem summary = summaries.get(summaryIndex);
            if(summary == null) {
                summary = new ResponsesApiResponse.SummaryItem();
                summary.setType("summary_text");
                summaries.set(summaryIndex, summary);
            }
            summary.setText(StringUtils.defaultString(summary.getText()) + event.getDelta());
            item.setSummary(summaries);
        } catch (Exception e) {
            log.warn("Failed to parse reasoning summary from event data", e);
        }
    }

    private ResponsesApiResponse.OutputItem getOrCreateOutputItem(ResponsesApiStreamEvent event, String type) {
        Integer outputIndex = event.getOutput_index();
        if(outputIndex == null && StringUtils.isBlank(event.getItem_id()) && "message".equals(type)) {
            outputIndex = 0;
        }
        if(outputIndex != null && outputIndex >= 0 && outputIndex < 1000) {
            while(outputItems.size() <= outputIndex) {
                outputItems.add(null);
            }
            ResponsesApiResponse.OutputItem item = outputItems.get(outputIndex);
            if(item == null) {
                item = new ResponsesApiResponse.OutputItem();
                item.setType(type);
                item.setId(event.getItem_id());
                outputItems.set(outputIndex, item);
            }
            return item;
        }

        if(StringUtils.isNotBlank(event.getItem_id())) {
            for (ResponsesApiResponse.OutputItem item : outputItems) {
                if(item != null && event.getItem_id().equals(item.getId())) {
                    return item;
                }
            }
        }
        ResponsesApiResponse.OutputItem item = new ResponsesApiResponse.OutputItem();
        item.setType(type);
        item.setId(event.getItem_id());
        outputItems.add(item);
        return item;
    }

    private void accumulateOutputItem(String eventData) {
        try {
            ResponsesApiStreamEvent event = JacksonUtils.deserialize(eventData, ResponsesApiStreamEvent.class);
            if(event == null || event.getItem() == null) {
                return;
            }

            Integer outputIndex = event.getOutput_index();
            if(outputIndex != null && outputIndex >= 0 && outputIndex < 1000) {
                while(outputItems.size() <= outputIndex) {
                    outputItems.add(null);
                }
                outputItems.set(outputIndex, event.getItem());
                return;
            }

            String itemId = event.getItem().getId();
            if(StringUtils.isNotBlank(itemId)) {
                for (int i = 0; i < outputItems.size(); i++) {
                    ResponsesApiResponse.OutputItem existing = outputItems.get(i);
                    if(existing != null && itemId.equals(existing.getId())) {
                        outputItems.set(i, event.getItem());
                        return;
                    }
                }
            }
            outputItems.add(event.getItem());
        } catch (Exception e) {
            log.warn("Failed to parse output item from event data", e);
        }
    }

    private SafetyCheckRequest.Chat buildSafetyCheckRequest() {
        ResponsesApiResponse tempResponse = ResponsesApiResponse.builder()
                .id(responseId)
                .output_text(safetyChecker.getBufferedText())
                .model(processData.getModel())
                .build();
        return SafetyCheckRequest.Chat.convertFrom(tempResponse, processData, apikeyInfo);
    }

    private void handleBlock(BellaException.SafetyCheckException e) {
        safetyChecker.markBlocked();
        log.warn("流式安全检测拦截(Responses API): requestId={}", processData.getRequestId());

        ResponsesApiResponse failedResponseForLog = ResponsesApiResponse.builder()
                .id(responseId)
                .status("failed")
                .usage(usage)
                .error(e.convertToOpenapiError())
                .build();
        fillStructuredOutput(failedResponseForLog);
        fillBufferedOutputText(failedResponseForLog);
        failedResponseForLog.setSensitives(e.getSensitive());
        terminalResponse = failedResponseForLog;

        ResponsesApiStreamEvent failedEvent = ResponsesApiStreamEvent.builder()
                .type("response.failed")
                .response(ResponsesApiResponse.builder()
                        .id(responseId)
                        .status("failed")
                        .error(e.convertToOpenapiError())
                        .build())
                .build();
        sendSseEvent("response.failed", JacksonUtils.serialize(failedEvent));

        if(sse != null) {
            sse.complete();
        }
        log();
    }

    private boolean isFailureTerminalEvent(String eventType) {
        return "response.failed".equals(eventType)
                || "response.incomplete".equals(eventType)
                || "response.cancelled".equals(eventType)
                || "response.error".equals(eventType);
    }

    private void extractResponseId(String eventData) {
        try {
            ResponsesApiStreamEvent event = JacksonUtils.deserialize(eventData, ResponsesApiStreamEvent.class);
            if(event != null && event.getResponse() != null && StringUtils.isNotBlank(event.getResponse().getId())) {
                this.responseId = event.getResponse().getId();
            }
        } catch (Exception e) {
            log.warn("Failed to extract responseId from response.created event", e);
        }
    }

    private void extractTerminalResponse(String eventData) {
        try {
            ResponsesApiStreamEvent event = JacksonUtils.deserialize(eventData, ResponsesApiStreamEvent.class);
            if(event == null || event.getResponse() == null) {
                return;
            }

            ResponsesApiResponse response = event.getResponse();
            this.terminalResponse = response;

            if(StringUtils.isNotBlank(response.getId())) {
                this.responseId = response.getId();
            }

            if(response.getUsage() != null) {
                this.usage = response.getUsage();
            }
            fillStructuredOutput(response);
        } catch (Exception e) {
            log.warn("Failed to extract response metadata from terminal event", e);
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
        long endMillis = DateTimeUtils.getCurrentMills();
        long endTime = endMillis / 1000;
        processData.setResponseMillis(endMillis);
        processData.setDuration(endTime - processData.getRequestTime());
        processData.setFirstPackageTime(firstPackageTime == null ? 0 : firstPackageTime);

        ResponsesApiResponse response = terminalResponse;
        if(response == null && (usage != null || StringUtils.isNotBlank(responseId) || StringUtils.isNotBlank(safetyChecker.getBufferedText()))) {
            response = new ResponsesApiResponse();
        }
        if(response != null) {
            if(StringUtils.isBlank(response.getId())) {
                response.setId(responseId);
            }
            if(response.getCreated() == null || response.getCreated() <= 0) {
                response.setCreated(endTime);
            }
            if(response.getUsage() == null) {
                response.setUsage(usage);
            }
            fillStructuredOutput(response);
            processData.setResponse(response);
        }

        if(StringUtils.isNotBlank(responseId)) {
            processData.setChannelRequestId(responseId);
        }

        logger.log(processData);
    }

    private void fillBufferedOutputText(ResponsesApiResponse response) {
        if(response != null && StringUtils.isBlank(response.getOutput_text())
                && StringUtils.isNotBlank(safetyChecker.getBufferedText())) {
            response.setOutput_text(safetyChecker.getBufferedText());
        }
    }

    private void fillStructuredOutput(ResponsesApiResponse response) {
        if(response == null || (response.getOutput() != null && !response.getOutput().isEmpty())) {
            return;
        }

        List<ResponsesApiResponse.OutputItem> reconstructedOutput = new ArrayList<>();
        for (ResponsesApiResponse.OutputItem item : outputItems) {
            if(item != null) {
                reconstructedOutput.add(item);
            }
        }

        String outputText = StringUtils.defaultIfBlank(safetyChecker.getBufferedText(), response.getOutput_text());
        if(StringUtils.isNotBlank(outputText) && !hasTextOutput(reconstructedOutput)) {
            attachTextOutput(reconstructedOutput, outputText, response.getStatus());
        }
        if(!reconstructedOutput.isEmpty()) {
            response.setOutput(reconstructedOutput);
        }
    }

    private boolean hasTextOutput(List<ResponsesApiResponse.OutputItem> items) {
        for (ResponsesApiResponse.OutputItem item : items) {
            if(item == null || item.getContent() == null) {
                continue;
            }
            for (ResponsesApiResponse.ContentItem content : item.getContent()) {
                if(content != null && StringUtils.isNotBlank(content.getText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void attachTextOutput(List<ResponsesApiResponse.OutputItem> items, String text, String status) {
        for (ResponsesApiResponse.OutputItem item : items) {
            if(item != null && ("message".equals(item.getType()) || "message_output".equals(item.getType()))) {
                ResponsesApiResponse.ContentItem content = new ResponsesApiResponse.ContentItem();
                content.setType("output_text");
                content.setText(text);
                item.setContent(java.util.Collections.singletonList(content));
                return;
            }
        }
        items.add(buildTextOutputItem(text, status));
    }

    private ResponsesApiResponse.OutputItem buildTextOutputItem(String text, String status) {
        ResponsesApiResponse.ContentItem content = new ResponsesApiResponse.ContentItem();
        content.setType("output_text");
        content.setText(text);

        ResponsesApiResponse.OutputItem outputItem = new ResponsesApiResponse.OutputItem();
        outputItem.setType("message");
        outputItem.setRole("assistant");
        outputItem.setStatus(status);
        outputItem.setContent(java.util.Collections.singletonList(content));
        return outputItem;
    }

}
