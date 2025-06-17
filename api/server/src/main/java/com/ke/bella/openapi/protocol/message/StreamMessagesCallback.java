package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.ChannelException;
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

    private Integer curIndex = -1;

    private boolean isToolCall;

    private boolean isSendFinish;

    public StreamMessagesCallback(SseEmitter sse,
            EndpointProcessData processData, ApikeyInfo apikeyInfo,
            EndpointLogger logger,
            ISafetyCheckService.IChatSafetyCheckService safetyService) {
        super(sse, processData, apikeyInfo, logger, safetyService);
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        msg.setCreated(DateTimeUtils.getCurrentSeconds());
        if(CollectionUtils.isNotEmpty(msg.getChoices())) {
            StreamCompletionResponse.Choice streamChoice = msg.getChoices().get(0);
            if(CollectionUtils.isNotEmpty(streamChoice.getDelta().getTool_calls())) {
                isToolCall = true;
            }
        }
        List<StreamMessageResponse> messages = TransferUtils.convertStreamResponse(msg, isToolCall);
        if(CollectionUtils.isNotEmpty(messages)) {
            if(first) {
                send(StreamMessageResponse.messageStart(StreamMessageResponse.initial(msg)));
                first = false;
            }
            if(CollectionUtils.isNotEmpty(msg.getChoices())) {
                StreamCompletionResponse.Choice streamChoice = msg.getChoices().get(0);
                if(curIndex != streamChoice.getIndex()) {
                    if(curIndex >= 0) {
                        send(StreamMessageResponse.contentBlockStop(curIndex));
                    }
                    if(!messages.get(0).getType().equals("content_block_start")) {
                        MessageResponse.ContentBlock contentBlock;
                        if(streamChoice.getDelta().getReasoning_content() != null) {
                            contentBlock = new MessageResponse.ResponseThinkingBlock("", null);
                        } else {
                            contentBlock = new MessageResponse.ResponseTextBlock("");
                        }
                        send(StreamMessageResponse.contentBlockStart(streamChoice.getIndex(), contentBlock));
                    }
                    curIndex = streamChoice.getIndex();
                }
            }
            if(messages.get(messages.size() - 1).getType().equals("message_delta")) {
                isSendFinish = true;
                messages.add(messages.size() - 1, StreamMessageResponse.contentBlockStop(curIndex));
            }
            messages.forEach(this::send);
        }
        updateBuffer(msg.getStandardFormat() == null ? msg : msg.getStandardFormat());
    }

    @Override
    public void done() {
        if(!isSendFinish) {
            send(StreamMessageResponse.contentBlockStop(curIndex));
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

    private void send(StreamMessageResponse data) {
        SseHelper.sendEvent(sse, data.getType(), data);
    }

}
