package com.ke.bella.openapi.protocol.completion;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.ke.bella.openapi.protocol.OpenapiResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;

@Data
@JsonInclude(Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CompletionResponse extends OpenapiResponse {

    private List<Choice> choices;
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

    /**
     * This fingerprint represents the backend configuration that the model runs with.<br/><br/> Can be used in conjunction with the seed request
     * parameter to understand when backend changes have been made that might impact determinism.
     */
    private String system_fingerprint;

    /**
     * 调用接口
     */
    private String object;

    private TokenUsage usage;

    @Data
    @JsonInclude(Include.NON_NULL)
    public static class Choice {

        /**
         * Every response will include a finish_reason. The possible values for finish_reason are:
         * <p>
         * stop: API returned complete message, or a message terminated by one of the stop sequences provided via the stop parameter length:
         * Incomplete model output due to max_tokens parameter or token limit function_call: The model decided to call a function content_filter:
         * Omitted content due to a flag from our content filters null: API response still in progress or incomplete
         */
        private String finish_reason;
        private int index;
        private Message message;
    }

    @Data
    public static class TokenUsage {
        private int completion_tokens;
        private int prompt_tokens;
        private int total_tokens;

        public TokenUsage add(TokenUsage u) {
            this.completion_tokens += u.completion_tokens;
            this.prompt_tokens += u.prompt_tokens;
            this.total_tokens += u.total_tokens;
            return this;
        }
    }

    public static CompletionResponse aggregate(List<StreamCompletionResponse> list) {
        CompletionResponse response = null;
        for(StreamCompletionResponse streamResponse : list) {
            if(CollectionUtils.isEmpty(streamResponse.getChoices())) {
                continue;
            }
            if(response == null) {
                response = streamResponse.convert();
            } else {
                int index = streamResponse.getChoices().get(0).getIndex();
                String content = (String) streamResponse.getChoices().get(0).getDelta().getContent();
                //拼接当前choice内容
                //判断当前choice对应的index是否已存在
                boolean newChoice = true;
                for (CompletionResponse.Choice choice : response.getChoices()) {
                    if(choice.getIndex() == index) {
                        newChoice = false;
                        if(content != null) {
                            if(choice.getMessage().getContent() == null) {
                                choice.getMessage().setContent(content);
                            } else {
                                choice.getMessage().setContent(choice.getMessage().getContent() + content);
                            }
                        } else if(CollectionUtils.isNotEmpty(streamResponse.getChoices().get(0).getDelta().getTool_calls())) {
                            //拼接function的arguments
                            //拼接对应index的function
                            int toolIndex = streamResponse.getChoices().get(0).getDelta().getTool_calls().get(0).getIndex();
                            String arguments = streamResponse.getChoices().get(0).getDelta().getTool_calls().get(0).getFunction().getArguments();
                            if(arguments == null) {
                                continue;
                            }
                            for (Message.ToolCall toolCall : choice.getMessage().getTool_calls()) {
                                if(toolCall.getIndex() == toolIndex) {
                                    if(toolCall.getFunction().getArguments() == null) {
                                        toolCall.getFunction().setArguments(arguments);
                                    } else {
                                        toolCall.getFunction().setArguments(toolCall.getFunction().getArguments() + arguments);
                                    }
                                }
                            }
                        }
                    }
                }
                if(newChoice) {
                    response.getChoices().add(streamResponse.getChoices().get(0).convert());
                }
                response.setUsage(streamResponse.getUsage());
            }
        }
        return response;
    }

}

