package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Lists;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.Message.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamCompletionResponse extends OpenapiResponse {
    public static final String CHAT_COMPLETION_CHUNK_OBJECT = "chat.completion.chunk";

    /**
     * 时间戳
     */
    private long created;
    /**
     * 唯一id
     */
    private String id;
    /**
     * 调用模型
     */
    private String model;

    private CompletionResponse.TokenUsage usage;

    private List<Choice> choices;

    @JsonIgnore
    private StreamCompletionResponse standardFormat;

    public List<Choice> getChoices() {
        return choices == null ? Lists.newArrayList() : choices;
    }

    public String content() {
        if(CollectionUtils.isNotEmpty(choices)) {
            return choices.get(0).content();
        } else {
            return "";
        }
    }

    public String reasoning() {
        if(CollectionUtils.isNotEmpty(choices)) {
            return choices.get(0).reasoning();
        } else {
            return "";
        }
    }

    public List<ToolCall> toolCalls() {
        if(CollectionUtils.isNotEmpty(choices)) {
            return choices.get(0).getDelta().getTool_calls();
        } else {
            return null;
        }
    }

    public void setContent(String content) {
        Assert.notEmpty(choices, "choices must not be null");
        choices.get(0).setContent(content);
    }

    public void setReasoning(String reasoning) {
        Assert.notNull(choices, "choices must not be null");
        choices.get(0).setReasoning(reasoning);
    }

    public void setToolCall(ToolCall toolCall) {
        Assert.notNull(choices, "choices must not be null");
        choices.get(0).setTooCall(toolCall);
    }

    public void setFinishReason(String finishReason) {
        Assert.notNull(choices, "choices must not be null");
        choices.get(0).setFinish_reason(finishReason);
    }

    public String finishReason() {
        if(CollectionUtils.isNotEmpty(choices)
                && choices.get(0).getFinish_reason() != null) {
            return choices.get(0).getFinish_reason();
        } else {
            return "";
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Choice {

        /**
         * Every response will include a finish_reason. The possible values for
         * finish_reason are:
         * <p>
         * stop: API returned complete message, or a message terminated by one
         * of the stop sequences provided via the stop parameter length:
         * Incomplete model output due to max_tokens parameter or token limit
         * function_call: The model decided to call a function content_filter:
         * Omitted content due to a
         * flag from our content filters null: API response still in progress or
         * incomplete
         */
        private String finish_reason;
        private int index;
        private Message delta;

        public String content() {
            if(delta != null && delta.getContent() != null) {
                return delta.getContent().toString();
            } else {
                return "";
            }
        }

        public String reasoning() {
            if(delta != null) {
                return delta.getReasoning_content();
            } else {
                return "";
            }
        }

        public void setContent(String content) {
            Assert.notNull(delta, "delta must not be null");
            delta.setContent(content);
        }

        public void setReasoning(String reasoning) {
            Assert.notNull(delta, "delta must not be null");
            delta.setReasoning_content(reasoning);
        }

        public void setTooCall(ToolCall toolCall) {
            Assert.notNull(delta, "delta must not be null");
            delta.setTool_calls(Lists.newArrayList(toolCall));
        }
    }

    public static List<Choice> toolcallChoice(ToolCall fc) {
        Choice choice = Choice.builder()
                .delta(Message.builder()
                        .role("assistant")
                        .tool_calls(Arrays.asList(fc))
                        .tool_call_id(fc.getId())
                        .build())
                .build();
        return Arrays.asList(choice);

    }

    public static List<Choice> assistantMessageChoice(String delta) {
        Choice choice = Choice.builder()
                .delta(Message.builder()
                        .role("assistant")
                        .content(delta)
                        .build())
                .build();
        return Arrays.asList(choice);
    }

}
