package com.ke.bella.openapi.protocol.completion.callback;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.ResponseHelper;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import org.apache.commons.lang3.StringUtils;

public class SplitReasoningCallback extends Callbacks.StreamCompletionCallbackNode {

    private final CompletionProperty property;

    private Integer thinkStage = 0; // 0: 推理未开始; 1: 推理开始; 2: 推理进行中；3:
                                    // 推理完成；-1:推理已结束

    public SplitReasoningCallback(CompletionProperty property) {
        this.property = property;
    }

    @Override
    public StreamCompletionResponse doCallback(StreamCompletionResponse msg) {
        thinkStage = getThinkStage(msg, thinkStage);
        splitThinkResp(msg, thinkStage);
        return msg;
    }

    private Integer getThinkStage(StreamCompletionResponse msg, Integer currentStage) {
        String content = msg.content();
        if(currentStage == 0 && content.startsWith(ResponseHelper.START_THINK)) {
            return 1;
        }
        if(currentStage == 1 && StringUtils.isNotEmpty(content)) {
            return 2;
        }
        if(currentStage == 2 && content.contains(ResponseHelper.END_THINK)) {
            return 3;
        }
        if(currentStage == 3) {
            return -1;
        }
        return currentStage;
    }

    private void splitThinkResp(StreamCompletionResponse resp, Integer stage) {
        if(stage == 1) {
            handleStartThink(resp);
        } else if(stage == 2) {
            resp.setReasoning(resp.content());
            resp.setContent(StringUtils.EMPTY);
        } else if(stage == 3) {
            handleEndThink(resp);
        }
    }

    private static void handleStartThink(StreamCompletionResponse msg) {
        msg.setReasoning(msg.content().substring(ResponseHelper.START_THINK.length()));
        msg.setContent(StringUtils.EMPTY);
    }

    private static void handleEndThink(StreamCompletionResponse msg) {
        String content = msg.content();
        String[] parts = content.split(ResponseHelper.END_THINK);
        if(parts.length == 2) {
            msg.setReasoning(parts[0]);
            msg.setContent(parts[1]);
        } else if(parts.length == 1) {
            if(content.startsWith(ResponseHelper.END_THINK)) {
                msg.setContent(parts[0]);
            } else {
                msg.setReasoning(parts[0]);
            }
        } else {
            msg.setContent(StringUtils.EMPTY);
        }
    }

    @Override
    public boolean support() {
        return property.isSplitReasoningFromContent();
    }
}
