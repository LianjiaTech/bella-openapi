package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Responses API 协议转换器
 * 实现 Chat Completion 和 Responses API 之间的双向转换
 */
@Slf4j
public class ResponsesApiConverter {

    /**
     * 将 Chat Completion 请求转换为 Responses API 请求
     * @return Responses API 请求
     */
    public static ResponsesApiRequest convertChatCompletionToResponses(CompletionRequest chatRequest, String akCode) {
        ResponsesApiRequest.ResponsesApiRequestBuilder builder = ResponsesApiRequest.builder();

        // 基本参数映射
        builder.model(chatRequest.getModel())
                .prompt_cache_key(chatRequest.getPrompt_cache_key() == null ? akCode : chatRequest.getPrompt_cache_key())
                .frequency_penalty(chatRequest.getFrequency_penalty())
                .presence_penalty(chatRequest.getPresence_penalty())
                .stream(chatRequest.isStream())
                .store(false)
                .background(false)
                .previous_response_id(null);

        // 转换消息列表为 input 数组
        List<ResponsesApiRequest.InputItem> inputItems = convertMessagesToInput(chatRequest.getMessages());
        builder.input(inputItems);

        // 转换工具定义
        if (CollectionUtils.isNotEmpty(chatRequest.getTools())) {
            List<ResponsesApiRequest.ResponsesApiTool> responsesTools = convertToolsToResponsesApi(chatRequest.getTools());
            builder.tools(responsesTools);
        }

        // 工具选择配置
        if (chatRequest.getTool_choice() != null) {
            builder.tool_choice(chatRequest.getTool_choice());
        }

        // 推理内容配置
        if (chatRequest.getReasoning_effort() != null) {
            ResponsesApiRequest.ReasoningConfig reasoning = ResponsesApiRequest.ReasoningConfig.builder()
                    .effort(chatRequest.getReasoning_effort().toString())
                    .summary("auto")
                    .build();
            builder.reasoning(reasoning);
        }

        return builder.build();
    }

    /**
     * 将消息列表转换为 Responses API 的 input 格式
     */
    private static List<ResponsesApiRequest.InputItem> convertMessagesToInput(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return Collections.emptyList();
        }

        List<ResponsesApiRequest.InputItem> inputItems = new ArrayList<>();

        for (Message message : messages) {
            if ("tool".equals(message.getRole())) {
                // 工具调用结果转换为 function_call_output
                ResponsesApiRequest.InputItem outputItem = ResponsesApiRequest.InputItem.builder()
                        .type("function_call_output")
                        .call_id(message.getTool_call_id())
                        .output(message.getContent() == null ? "" : message.getContent().toString())
                        .status("completed")
                        .build();
                inputItems.add(outputItem);
            } else if (message.getTool_calls() != null && !message.getTool_calls().isEmpty()) {
                // 助手的工具调用转换
                for (Message.ToolCall toolCall : message.getTool_calls()) {
                    ResponsesApiRequest.InputItem callItem = ResponsesApiRequest.InputItem.builder()
                            .type("function_call")
                            .call_id(toolCall.getId())
                            .name(toolCall.getFunction().getName())
                            .arguments(toolCall.getFunction().getArguments())
                            .status("completed")
                            .build();
                    inputItems.add(callItem);
                }
            } else {
                // 普通消息转换
                ResponsesApiRequest.InputItem messageItem = ResponsesApiRequest.InputItem.builder()
                        .type("message")
                        .role(message.getRole())
                        .content(convertMessageContent(message))
                        .build();
                if(messageItem.getContent() != null) {
                    inputItems.add(messageItem);
                }
            }
        }

        return inputItems;
    }

    /**
     * 转换消息内容（支持多模态）
     */
    private static Object convertMessageContent(Message message) {
        if (message.getContent() == null) {
            return null;
        }

        // 如果是字符串内容，直接返回
        if (message.getContent() instanceof String) {
            return message.getContent();
        }

        // 如果是复杂内容（多模态），转换为 Responses API 格式
        if (message.getContent() instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) message.getContent();
            
            List<ResponsesApiRequest.ContentItem> responsesContent = new ArrayList<>();
            for (Map<String, Object> item : contentList) {
                String type = (String) item.get("type");
                ResponsesApiRequest.ContentItem.ContentItemBuilder builder = ResponsesApiRequest.ContentItem.builder();

                switch (type) {
                    case "text":
                        if(message.getRole().equals("assistant")) {
                            builder.type("output_text").text((String) item.get("text"));
                        } else {
                            builder.type("input_text").text((String) item.get("text"));
                        }
                        break;
                    case "image_url":
                        @SuppressWarnings("unchecked")
                        Map<String, Object> imageUrl = (Map<String, Object>) item.get("image_url");
                        builder.type("input_image")
                                .image_url((String) imageUrl.get("url"))
                                .detail((String) imageUrl.get("detail"));
                        break;
                    case "image_file":
                        builder.type("input_image").file_id((String) item.get("file_id"));
                        break;
                    case "audio":
                        builder.type("input_audio").audio_url((String) item.get("url"));
                        break;
                    default:
                        // 其他类型保持原样
                        builder.type(type);
                        break;
                }
                responsesContent.add(builder.build());
            }
            return responsesContent;
        }

        return message.getContent();
    }

    /**
     * 转换工具定义到 Responses API 格式
     */
    private static List<ResponsesApiRequest.ResponsesApiTool> convertToolsToResponsesApi(List<Message.Tool> tools) {
        return tools.stream().map(tool -> ResponsesApiRequest.ResponsesApiTool.builder()
                .type(tool.getType())
                .name(tool.getFunction().getName())
                .description(tool.getFunction().getDescription())
                .parameters(tool.getFunction().getParameters())
                .strict(false) // 不使用严格模式，适配chat completion
                .build()).collect(Collectors.toList());
    }

    /**
     * 将 Responses API 响应转换为 Chat Completion 响应
     * 
     * @param responsesResponse Responses API 响应
     * @return Chat Completion 响应
     */
    public static CompletionResponse convertResponsesToChatCompletion(ResponsesApiResponse responsesResponse) {
        CompletionResponse.CompletionResponseBuilder builder = CompletionResponse.builder();
        builder.id(responsesResponse.getId())
                .object("chat.completion")
                .created(responsesResponse.getCreated() != null ? responsesResponse.getCreated() : System.currentTimeMillis() / 1000)
                .model(responsesResponse.getModel())
                .error(responsesResponse.getError()); // Responses API 不提供此字段

        // 转换 usage 信息
        if (responsesResponse.getUsage() != null) {
            builder.usage(convertToken(responsesResponse.getUsage()));
        }

        // 转换输出为 choices
        List<CompletionResponse.Choice> choices = convertOutputToChoices(responsesResponse);
        builder.choices(choices);

        // 设置完成原因
        String finishReason = determineFinishReason(responsesResponse);
        if (CollectionUtils.isNotEmpty(choices)) {
            choices.get(0).setFinish_reason(finishReason);
        }

        return builder.build();
    }

    public static CompletionResponse.TokenUsage convertToken(ResponsesApiResponse.Usage usage) {
        CompletionResponse.TokenUsage tokenUsage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(usage.getInput_tokens())
                .completion_tokens(usage.getOutput_tokens())
                .total_tokens(usage.getTotal_tokens())
                .build();
        if(usage.getInput_tokens_details() != null) {
            CompletionResponse.TokensDetail detail = new CompletionResponse.TokensDetail();
            detail.setCached_tokens(usage.getInput_tokens_details().getCached_tokens());
            tokenUsage.setPrompt_tokens_details(detail);
            tokenUsage.setCache_read_tokens(detail.getCached_tokens());
        }
        if(usage.getOutput_tokens_details() != null) {
            CompletionResponse.TokensDetail detail = new CompletionResponse.TokensDetail();
            detail.setReasoning_tokens(usage.getOutput_tokens_details().getReasoning_tokens());
            tokenUsage.setCompletion_tokens_details(detail);
        }
        return tokenUsage;
    }

    /**
     * 将 Responses API 输出转换为 Chat Completion choices
     */
    private static List<CompletionResponse.Choice> convertOutputToChoices(ResponsesApiResponse responsesResponse) {
        CompletionResponse.Choice.ChoiceBuilder choiceBuilder = CompletionResponse.Choice.builder().index(0);

        String assistantContent = "";
        String reasoningContent = null;
        List<Message.ToolCall> toolCalls = new ArrayList<>();

        // 如果有简单的输出文本，直接使用
        if (StringUtils.isNotBlank(responsesResponse.getOutput_text())) {
            assistantContent = responsesResponse.getOutput_text();
        }

        // 处理复杂输出项
        if (CollectionUtils.isNotEmpty(responsesResponse.getOutput())) {
            for (ResponsesApiResponse.OutputItem item : responsesResponse.getOutput()) {
                switch (item.getType()) {
                    case "message_output":
                    case "message":
                        // 提取消息内容
                        if (CollectionUtils.isNotEmpty(item.getContent())) {
                            StringBuilder content = new StringBuilder();
                            for (ResponsesApiResponse.ContentItem contentItem : item.getContent()) {
                                if ("text".equals(contentItem.getType()) || "output_text".equals(contentItem.getType())) {
                                    content.append(contentItem.getText());
                                }
                            }
                            assistantContent = content.toString();
                        }
                        break;
                    case "function_call":
                        // 转换工具调用
                        Message.ToolCall toolCall = Message.ToolCall.builder()
                                .id(item.getCall_id())
                                .type("function")
                                .function(Message.FunctionCall.builder()
                                        .name(item.getName())
                                        .arguments(item.getArguments())
                                        .build())
                                .build();
                        toolCalls.add(toolCall);
                        break;
                    case "reasoning":
                        // 提取推理内容
                        if (CollectionUtils.isNotEmpty(item.getSummary())) {
                            StringBuilder reasoning = new StringBuilder();
                            for (ResponsesApiResponse.SummaryItem summaryItem : item.getSummary()) {
                                if ("summary_text".equals(summaryItem.getType())) {
                                    reasoning.append(summaryItem.getText());
                                }
                            }
                            reasoningContent = reasoning.toString();
                        }
                        break;
                    default:
                        log.debug("Unknown output item type: {}", item.getType());
                        break;
                }
            }
        }

        // 构建消息
        Message.MessageBuilder messageBuilder = Message.builder().role("assistant");
        
        if (CollectionUtils.isNotEmpty(toolCalls)) {
            messageBuilder.tool_calls(toolCalls).content(null);
        } else {
            messageBuilder.content(assistantContent);
        }

        // 添加推理内容（扩展字段）
        if (StringUtils.isNotBlank(reasoningContent)) {
            messageBuilder.reasoning_content(reasoningContent);
        }

        choiceBuilder.message(messageBuilder.build());

        return Collections.singletonList(choiceBuilder.build());
    }

    /**
     * 确定完成原因
     */
    private static String determineFinishReason(ResponsesApiResponse responsesResponse) {
        if (!"completed".equals(responsesResponse.getStatus()) && responsesResponse.getStatus() != null) {
            switch (responsesResponse.getStatus()) {
                case "failed":
                    return "error";
                case "cancelled":
                    return "cancelled";
                case "in_progress":
                case "pending":
                    return null;
                default:
                    return "stop";
            }
        }

        // 检查是否有工具调用
        if (CollectionUtils.isNotEmpty(responsesResponse.getOutput())) {
            boolean hasToolCalls = responsesResponse.getOutput().stream()
                    .anyMatch(item -> "function_call".equals(item.getType()));
            if (hasToolCalls) {
                return "tool_calls";
            }
        }

        return "stop";
    }
}
