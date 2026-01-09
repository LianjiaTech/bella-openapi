package com.ke.bella.openapi.protocol.completion.callback;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

public class MergeReasoningCallback extends Callbacks.StreamCompletionCallbackNode {
    private final CompletionProperty property;

    private Integer thinkStage = 0; // 0: 推理未开始; 1: 推理开始; 2: 推理进行中；3:
                                    // 推理完成；-1:推理已结束

    public MergeReasoningCallback(CompletionProperty property) {
        this.property = property;
    }

    @Override
    public StreamCompletionResponse doCallback(StreamCompletionResponse msg) {
        thinkStage = getThinkStage(msg, thinkStage);
        return mergeThinkResp(msg, thinkStage);
    }

    private Integer getThinkStage(StreamCompletionResponse msg, Integer currentStage) {
        if(currentStage == 0 && StringUtils.isNotEmpty(msg.reasoning())) {
            return 1;
        }
        if(currentStage == 1 && StringUtils.isNotEmpty(msg.reasoning())) {
            return 2;
        }
        if(currentStage == 2 && (StringUtils.isNotEmpty(msg.content()) || msg.toolCalls() != null)) {
            return 3;
        }
        if(currentStage == 3 && (StringUtils.isNotEmpty(msg.content()) || msg.toolCalls() != null)) {
            return -1;
        }
        return currentStage;
    }

    public static StreamCompletionResponse mergeThinkResp(StreamCompletionResponse resp, Integer stage) {
        if(stage <= 0) {
            return resp;
        }
        StreamCompletionResponse response = new StreamCompletionResponse();
        response.setCreated(resp.getCreated());
        response.setChoices(new ArrayList<>());
        response.setUsage(resp.getUsage());
        response.setId(resp.getId());
        response.setModel(resp.getModel());
        Message thinkMessage = resp.getChoices().get(0).getDelta();
        Message message = new Message();
        message.setName(thinkMessage.getName());
        message.setContent(thinkMessage.getReasoning_content());
        message.setReasoning_content(thinkMessage.getReasoning_content());
        message.setTool_calls(thinkMessage.getTool_calls());
        if(stage == 1) {
            message.setContent("```sh\n" + message.getContent());
        } else if(stage == 3) {
            message.setContent("\n```\n" + (thinkMessage.getContent() == null ? "" : thinkMessage.getContent()));
        }
        response.getChoices().add(new StreamCompletionResponse.Choice("", 0, message, null));
        response.setStandardFormat(resp);
        return response;
    }

    @Override
    public boolean support() {
        return property.isMergeReasoningContent();
    }

}
