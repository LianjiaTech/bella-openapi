package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.protocol.completion.callback.StreamCompletionCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.SseHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
public class StreamMessagesCallback extends StreamCompletionCallback {

    private boolean first = true;

    private Integer curChoiceIndex = -1;

    private boolean isToolCall;

    private boolean isSendFinish;

    private int stage; // 0 - not started, 1 - thinking, 2 - text, 3 - tool call

    private int contentIndex = -1;

    public StreamMessagesCallback(SseEmitter sse,
            EndpointProcessData processData, ApikeyInfo apikeyInfo,
            EndpointLogger logger,
            ISafetyCheckService.IChatSafetyCheckService safetyService) {
        super(sse, processData, apikeyInfo, logger, safetyService);
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        if(firstPackageTime == null) {
            firstPackageTime = DateTimeUtils.getCurrentMills();
        }
        if(processData.isNativeSend()) {
            updateBuffer(msg.getStandardFormat() == null ? msg : msg.getStandardFormat());
            return;
        }
        msg.setCreated(DateTimeUtils.getCurrentSeconds());
        if(CollectionUtils.isNotEmpty(msg.getChoices())) {
            StreamCompletionResponse.Choice streamChoice = msg.getChoices().get(0);
            if(CollectionUtils.isNotEmpty(streamChoice.getDelta().getTool_calls())) {
                isToolCall = true;
            }
            if(curChoiceIndex != streamChoice.getIndex()) {
                contentIndex += 1;
            }
        }
        List<StreamMessageResponse> messages = TransferFromCompletionsUtils.convertStreamResponse(msg, isToolCall, contentIndex);
        if(CollectionUtils.isNotEmpty(messages)) {
            if(first) {
                send(StreamMessageResponse.messageStart(StreamMessageResponse.initial(msg, processData.getModel())));
                first = false;
            }
            if(CollectionUtils.isNotEmpty(msg.getChoices())) {
                StreamCompletionResponse.Choice streamChoice = msg.getChoices().get(0);
                if(curChoiceIndex != streamChoice.getIndex()) {
                    if(curChoiceIndex >= 0) {
                        send(StreamMessageResponse.contentBlockStop(contentIndex - 1));
                    }
                    if(!messages.get(0).getType().equals("content_block_start")) {
                        MessageResponse.ContentBlock contentBlock;
                        if(streamChoice.getDelta().getReasoning_content() != null || streamChoice.getDelta().getReasoning_content_signature() != null) {
                            contentBlock = new MessageResponse.ResponseThinkingBlock("", null);
                        } else {
                            contentBlock = new MessageResponse.ResponseTextBlock("");
                        }
                        send(StreamMessageResponse.contentBlockStart(contentIndex, contentBlock));
                    }
                    curChoiceIndex = streamChoice.getIndex();
                    stage = getCurrentStage(streamChoice);
                } else {
                    int currentStage = getCurrentStage(streamChoice);
                    if(stage == 3 && currentStage == 3) {
                        if(messages.get(0).getType().equals("content_block_start")) {
                            int index = getTargetIndex(messages, stage);
                            messages.add(index, StreamMessageResponse.contentBlockStop(contentIndex));
                            curChoiceIndex += 1;
                        }
                    } else if(currentStage != stage) {
                        stage = currentStage;
                        int index = getTargetIndex(messages, stage);
                        contentIndex += 1;
                        if(currentStage != 3) {
                            MessageResponse.ContentBlock contentBlock = currentStage == 2 ?  new MessageResponse.ResponseTextBlock("") : new MessageResponse.ResponseThinkingBlock("", null);;
                            messages.add(index, StreamMessageResponse.contentBlockStart(contentIndex, contentBlock));
                            messages.forEach(streamMessageResponse -> streamMessageResponse.setIndex(contentIndex));
                        }
                        messages.add(index, StreamMessageResponse.contentBlockStop(contentIndex - 1));
                    }
                }
            }

            if(messages.get(messages.size() - 1).getType().equals("message_delta") && !isSendFinish) {
                isSendFinish = true;
                messages.add(messages.size() - 1, StreamMessageResponse.contentBlockStop(contentIndex));
            }
            messages.forEach(this::send);
        }
        updateBuffer(msg.getStandardFormat() == null ? msg : msg.getStandardFormat());
    }

    private int getCurrentStage(StreamCompletionResponse.Choice streamChoice) {
        return streamChoice.getDelta() == null ? 0 :
                streamChoice.getDelta().getTool_calls() != null ? 3 :
                streamChoice.getDelta().getContent() != null ? 2 :
                streamChoice.getDelta().getReasoning_content() != null
                        || streamChoice.getDelta().getReasoning_content_signature() != null
                        || streamChoice.getDelta().getRedacted_reasoning_content() != null
                        ? 1 : 0;
    }

    private int getTargetIndex(List<StreamMessageResponse> messages, int stage) {
        for(int i = 0; i < messages.size(); i++) {
            StreamMessageResponse message = messages.get(i);
            if(message.getType().equals("content_block_start")) {
                return i;
            }
            Object delta = message.getDelta();
            if(stage == 2) {
                if(!(delta instanceof StreamMessageResponse.TextDelta)) {
                    return i;
                }
            }
            if(stage == 1) {
                if(!(delta instanceof StreamMessageResponse.ThinkingDelta || delta instanceof StreamMessageResponse.SignatureDelta || delta instanceof StreamMessageResponse.RedactedThinkingDelta)) {
                    return i;
                }
            }
        }
        return 0;
    }

    @Override
    public void done() {
        if(processData.isNativeSend()) {
            return;
        }
        if(!isSendFinish) {
            send(StreamMessageResponse.contentBlockStop(contentIndex));
            StreamMessageResponse.StreamUsage streamUsage = StreamMessageResponse.StreamUsage.builder()
                    .outputTokens(1)
                    .inputTokens(1)
                    .build();
            StreamMessageResponse.MessageDeltaInfo messageInfo = StreamMessageResponse.MessageDeltaInfo.builder()
                    .stopReason(isToolCall ? "tool_use" : "end_turn")
                    .build();
            send(StreamMessageResponse.messageDelta(messageInfo, streamUsage));
        }
        send(StreamMessageResponse.messageStop());
    }

    @Override
    public void send(Object data) {
        if(sse == null) {
            return;
        }
        if(data instanceof StreamMessageResponse) {
            SseHelper.sendEvent(sse, ((StreamMessageResponse)data).getType(), data);
        } else {
            throw new IllegalStateException("Only Support StreamMessageResponse");
        }
    }

}
