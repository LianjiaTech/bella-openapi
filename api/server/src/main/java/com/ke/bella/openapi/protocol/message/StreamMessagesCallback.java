package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.protocol.completion.callback.StreamCompletionCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.SseHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
            ISafetyCheckService<SafetyCheckRequest.Chat> safetyService) {
        super(sse, processData, apikeyInfo, logger, safetyService);
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        if(safetyChecker.isBlocked()) return;

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
            if(hasToolCalls(streamChoice)) {
                isToolCall = true;
            }
            int currentStage = getCurrentStage(streamChoice);
            if(currentStage == 0) {
                List<StreamMessageResponse> messages = TransferFromCompletionsUtils.convertStreamResponse(msg, isToolCall, contentIndex);
                if(CollectionUtils.isNotEmpty(messages)) {
                    if(first) {
                        send(StreamMessageResponse.messageStart(StreamMessageResponse.initial(msg, processData.getModel())));
                        first = false;
                    }
                    if(messages.get(messages.size() - 1).getType().equals("message_delta") && !isSendFinish && contentIndex >= 0) {
                        isSendFinish = true;
                        messages.add(messages.size() - 1, StreamMessageResponse.contentBlockStop(contentIndex));
                    }
                    messages.forEach(this::send);
                }
                updateBuffer(msg.getStandardFormat() == null ? msg : msg.getStandardFormat());
                try { safetyCheck(false); } catch (BellaException.SafetyCheckException e) { handleBlock(e); return; }
                return;
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
                int currentStage = getCurrentStage(streamChoice);
                if(curChoiceIndex != streamChoice.getIndex()) {
                    if(curChoiceIndex >= 0) {
                        send(StreamMessageResponse.contentBlockStop(contentIndex - 1));
                    }
                    if(!messages.get(0).getType().equals("content_block_start")) {
                        MessageResponse.ContentBlock contentBlock;
                        Message delta = streamChoice.getDelta();
                        if(delta != null && (delta.getReasoning_content() != null
                                || delta.getReasoning_content_signature() != null)) {
                            contentBlock = new MessageResponse.ResponseThinkingBlock("", null);
                        } else {
                            contentBlock = new MessageResponse.ResponseTextBlock("");
                        }
                        send(StreamMessageResponse.contentBlockStart(contentIndex, contentBlock));
                    }
                    curChoiceIndex = streamChoice.getIndex();
                    stage = currentStage;
                } else {
                    if(stage == 3 && currentStage == 3) {
                        if(messages.get(0).getType().equals("content_block_start")) {
                            contentIndex += 1;
                            int index = getTargetIndex(messages, stage);
                            messages.add(index, StreamMessageResponse.contentBlockStop(contentIndex - 1));
                            for (int i = 0; i < messages.size(); i++) {
                                if(i != index) {
                                    messages.get(i).setIndex(contentIndex);
                                }
                            }
                        }
                    } else if(currentStage != stage) {
                        int index = getTargetIndex(messages, stage);
                        contentIndex += 1;
                        if(currentStage != 3) {
                            MessageResponse.ContentBlock contentBlock = currentStage == 2 ? new MessageResponse.ResponseTextBlock("")
                                    : new MessageResponse.ResponseThinkingBlock("", null);
                            messages.add(index, StreamMessageResponse.contentBlockStart(contentIndex, contentBlock));
                            messages.forEach(streamMessageResponse -> streamMessageResponse.setIndex(contentIndex));
                        } else {
                            messages.forEach(streamMessageResponse -> streamMessageResponse.setIndex(contentIndex));
                        }
                        messages.add(index, StreamMessageResponse.contentBlockStop(contentIndex - 1));
                        stage = currentStage;
                    }
                }
            }

            if(messages.get(messages.size() - 1).getType().equals("message_delta") && !isSendFinish && contentIndex >= 0) {
                isSendFinish = true;
                messages.add(messages.size() - 1, StreamMessageResponse.contentBlockStop(contentIndex));
            }
            messages.forEach(this::send);
        }
        updateBuffer(msg.getStandardFormat() == null ? msg : msg.getStandardFormat());
        try { safetyCheck(false); } catch (BellaException.SafetyCheckException e) { handleBlock(e); }
    }

    private int getCurrentStage(StreamCompletionResponse.Choice streamChoice) {
        Message delta = streamChoice.getDelta();
        if(delta == null) {
            return 0;
        }
        if(CollectionUtils.isNotEmpty(delta.getTool_calls())) {
            return 3;
        }
        if(delta.getContent() instanceof String && StringUtils.isNotEmpty((String) delta.getContent())) {
            return 2;
        }
        if(delta.getContent() != null && !(delta.getContent() instanceof String)) {
            return 2;
        }
        if(StringUtils.isNotEmpty(delta.getReasoning_content())
                || StringUtils.isNotEmpty(delta.getReasoning_content_signature())
                || StringUtils.isNotEmpty(delta.getRedacted_reasoning_content())) {
            return 1;
        }
        return 0;
    }

    private boolean hasToolCalls(StreamCompletionResponse.Choice streamChoice) {
        return streamChoice.getDelta() != null && CollectionUtils.isNotEmpty(streamChoice.getDelta().getTool_calls());
    }

    private int getTargetIndex(List<StreamMessageResponse> messages, int stage) {
        for (int i = 0; i < messages.size(); i++) {
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
                if(!(delta instanceof StreamMessageResponse.ThinkingDelta || delta instanceof StreamMessageResponse.SignatureDelta
                        || delta instanceof StreamMessageResponse.RedactedThinkingDelta)) {
                    return i;
                }
            }
        }
        return 0;
    }

    @Override
    public void done() {
        if(safetyChecker.isBlocked()) return;

        try { safetyCheck(true); } catch (BellaException.SafetyCheckException e) { handleBlock(e); return; }

        sendSafetyResults(true);

        if(processData.isNativeSend()) {
            return;
        }
        if(!isSendFinish) {
            if(contentIndex >= 0) {
                send(StreamMessageResponse.contentBlockStop(contentIndex));
            }
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
    protected void handleBlock(BellaException.SafetyCheckException e) {
        safetyChecker.markBlocked();
        log.warn("流式安全检测拦截，中断流(Messages): requestId={}", processData.getRequestId());

        // 正确关闭已打开的 content block 和 message 生命周期
        if(!isSendFinish) {
            if(contentIndex >= 0) {
                send(StreamMessageResponse.contentBlockStop(contentIndex));
            }
            StreamMessageResponse.StreamUsage streamUsage = StreamMessageResponse.StreamUsage.builder()
                    .outputTokens(1)
                    .inputTokens(1)
                    .build();
            StreamMessageResponse.MessageDeltaInfo messageInfo = StreamMessageResponse.MessageDeltaInfo.builder()
                    .stopReason("refusal")
                    .build();
            send(StreamMessageResponse.messageDelta(messageInfo, streamUsage));
        }
        // 发送 error 事件携带拦截原因
        OpenapiResponse.OpenapiError openapiError = e.convertToOpenapiError();
        send(StreamMessageResponse.error(openapiError.getType(), openapiError.getMessage()));
        send(StreamMessageResponse.messageStop());
    }

    @Override
    public void send(Object data) {
        if(sse == null) {
            return;
        }
        if(data instanceof StreamMessageResponse) {
            SseHelper.sendEvent(sse, ((StreamMessageResponse) data).getType(), data);
        } else {
            throw new IllegalStateException("Only Support StreamMessageResponse");
        }
    }

}
