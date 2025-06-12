package com.ke.bella.openapi.protocol.completion.callback;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import com.ke.bella.openapi.protocol.message.StreamMessageResponse;
import com.ke.bella.openapi.protocol.message.TransferUtils;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.SseHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class StreamMessagesCallback extends StreamCompletionCallback {

    private boolean first = true;

    private Integer curIndex = -1;

    public StreamMessagesCallback(SseEmitter sse,
            EndpointProcessData processData, ApikeyInfo apikeyInfo,
            EndpointLogger logger,
            ISafetyCheckService.IChatSafetyCheckService safetyService) {
        super(sse, processData, apikeyInfo, logger, safetyService);
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        msg.setCreated(DateTimeUtils.getCurrentSeconds());
        StreamMessageResponse message = TransferUtils.convertStreamResponse(msg);
        if(message != null) {
            if(first) {
                send(StreamMessageResponse.messageStart(StreamMessageResponse.initial(msg)));
                first = false;
            }
            if(CollectionUtils.isNotEmpty(msg.getChoices())) {
                StreamCompletionResponse.Choice streamChoice = msg.getChoices().get(0);
                if(curIndex != streamChoice.getIndex()) {
                    if(!message.getType().equals("content_block_start")) {
                        MessageResponse.ContentBlock contentBlock;
                        if(streamChoice.getDelta().getReasoning_content() != null) {
                            contentBlock = new MessageResponse.ResponseThinkingBlock("");
                        } else {
                            contentBlock = new MessageResponse.ResponseTextBlock("");
                        }
                        send(StreamMessageResponse.contentBlockStart(streamChoice.getIndex(), contentBlock));
                    }
                    curIndex = streamChoice.getIndex();
                } else if(streamChoice.getFinish_reason() != null) {
                    send(StreamMessageResponse.contentBlockStop(curIndex));
                }
            }
            send(message);
        }
        updateBuffer(msg.getStandardFormat() == null ? msg : msg.getStandardFormat());
    }

    @Override
    public void done() {
        send(StreamMessageResponse.messageStop());
    }

    private void send(StreamMessageResponse data) {
        SseHelper.sendEvent(sse, data.getType(), data);
    }

}
