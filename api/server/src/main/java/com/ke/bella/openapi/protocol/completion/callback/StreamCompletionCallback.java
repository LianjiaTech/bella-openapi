package com.ke.bella.openapi.protocol.completion.callback;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ResponseHelper;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.PunctuationUtils;
import com.ke.bella.openapi.utils.SseHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class StreamCompletionCallback implements Callbacks.StreamCompletionCallback {
    @Getter
    private final SseEmitter sse;
    private final EndpointProcessData processData;
    private final ApikeyInfo apikeyInfo;
    private final EndpointLogger logger;
    private final ISafetyCheckService.IChatSafetyCheckService safetyService;
    private final CompletionResponse responseBuffer;
    private final Map<Integer, CompletionResponse.Choice> choiceBuffer;
    private boolean dirtyChoice;
    private Long firstPackageTime;
    private Object requestRiskData;
    private Integer safetyCheckIndex;
    private Integer thinkStage = 0; // 0: 推理未开始; 1: 推理开始; 2: 推理进行中；3:推理完成；-1:推理已结束

    public StreamCompletionCallback(SseEmitter sse, EndpointProcessData processData, ApikeyInfo apikeyInfo,
            EndpointLogger logger, ISafetyCheckService.IChatSafetyCheckService safetyService) {
        this.sse = sse;
        this.processData = processData;
        this.apikeyInfo = apikeyInfo;
        this.logger = logger;
        this.safetyService = safetyService;
        this.safetyCheckIndex = 0;
        this.responseBuffer = new CompletionResponse();
        responseBuffer.setCreated(DateTimeUtils.getCurrentSeconds());
        this.choiceBuffer = new HashMap<>();
        this.requestRiskData = processData.getRequestRiskData();
    }

    @Override
    public void onOpen() {

    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        msg.setCreated(DateTimeUtils.getCurrentSeconds());
        if(requestRiskData != null) {
            msg.setRequestRiskData(requestRiskData);
            requestRiskData = null;
        }
        SseHelper.sendEvent(sse, msg);
        updateBuffer(msg.getStandardFormat() == null ? msg : msg.getStandardFormat());
        safetyCheck(false);
    }

    @Override
    public void done() {
        safetyCheck(true);
        SseHelper.sendEvent(sse, "[DONE]");
    }

    public void finish() {
        sse.complete();
        log();
    }

    @Override
    public void finish(ChannelException exception) {
        OpenapiResponse.OpenapiError openapiError = exception.convertToOpenapiError();
        StreamCompletionResponse response = StreamCompletionResponse.builder()
                .created(DateTimeUtils.getCurrentSeconds())
                .error(openapiError)
                .build();
        try {
            callback(response);
        } finally {
            sse.completeWithError(exception);
            log();
        }
    }

    private void updateBuffer(StreamCompletionResponse streamResponse) {
        ResponseHelper.overwrite(responseBuffer, streamResponse);
        if(CollectionUtils.isEmpty(streamResponse.getChoices()) || streamResponse.getChoices().get(0).getDelta() == null) {
            return;
        }
        thinkStage = getThinkStage(streamResponse, thinkStage);
        if(thinkStage == 1 || thinkStage == 3) {
            safetyCheckIndex = 0;
        }
        if(firstPackageTime == null) {
            firstPackageTime = DateTimeUtils.getCurrentMills();
        }
        StreamCompletionResponse.Choice choice = streamResponse.getChoices().get(0);
        Integer choiceIndex = choice.getIndex();
        if(!choiceBuffer.containsKey(choiceIndex)) {
            choiceBuffer.put(choiceIndex, ResponseHelper.convert(choice));
        } else {
            ResponseHelper.combineMessage(choiceBuffer.get(choiceIndex).getMessage(), choice.getDelta());
            choiceBuffer.get(choiceIndex).setFinish_reason(choice.getFinish_reason());
        }
        if(isSafetyCheckChoice(choice)) {
            dirtyChoice = true;
        }
    }

    private Integer getThinkStage(StreamCompletionResponse msg, Integer currentStage) {
        if(currentStage == 0 && StringUtils.isNotEmpty(msg.reasoning())) {
            return 1;
        }
        if(currentStage == 1 && StringUtils.isNotEmpty(msg.reasoning())) {
            return 2;
        }
        if(currentStage == 2 && StringUtils.isNotEmpty(msg.content())) {
            return 3;
        }
        if(currentStage == 3 && StringUtils.isNotEmpty(msg.content())) {
            return -1;
        }
        return currentStage;
    }

    private boolean isSafetyCheckChoice(StreamCompletionResponse.Choice choice) {
        if(choice.getIndex() != 0) {
            return false;
        }
        return StringUtils.isNotBlank(choice.content()) || StringUtils.isNotBlank(choice.reasoning());
    }

    private void log() {
        CompletionResponse response = responseBuffer;
        long created = response.getCreated() <= 0 ? DateTimeUtils.getCurrentSeconds() : response.getCreated();
        processData.setDuration(created - processData.getRequestTime());
        processData.setFirstPackageTime(firstPackageTime == null ? DateTimeUtils.getCurrentMills() : firstPackageTime);
        response.setChoices(Lists.newArrayList(choiceBuffer.values()));
        processData.setResponse(response);
        logger.log(processData);
    }

    private void safetyCheck(boolean done) {
        if(!dirtyChoice) {
            return;
        }
        CompletionResponse.Choice choice = choiceBuffer.get(0);
        if(!done) {
            String content = thinkStage == 2 ? choice.reasoning() : choice.content();
            String delta = content.substring(safetyCheckIndex);
            if(!PunctuationUtils.endsWithPunctuation(delta)) {
                return;
            }
            safetyCheckIndex = content.length();
        }
        responseBuffer.setChoices(Collections.singletonList(choice));
        Object result = safetyService.safetyCheck(SafetyCheckRequest.Chat.convertFrom(responseBuffer, processData, apikeyInfo), processData.isMock());
        if(result != null) {
            StreamCompletionResponse response = new StreamCompletionResponse();
            response.setSensitives(result);
            response.setCreated(DateTimeUtils.getCurrentSeconds());
            SseHelper.sendEvent(sse, response);
        }
        dirtyChoice = false;
    }
}
