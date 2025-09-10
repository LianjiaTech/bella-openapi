package com.ke.bella.openapi.protocol.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class TransferFromCompletionsUtils {

    //Static inner classes for Content Parts, as per OpenAI structure
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    static class ContentPart {
        public String type;
        public String text;
        public ImageUrl image_url;
        public Object cache_control;

        private ContentPart(String type) {
            this.type = type;
        }

        public static ContentPart ofText(String text) {
            ContentPart part = new ContentPart("text");
            part.text = text;
            return part;
        }

        public static ContentPart ofImageUrl(ImageUrl imageUrl) {
            ContentPart part = new ContentPart("image_url");
            part.image_url = imageUrl;
            return part;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ImageUrl {
        public String url;
        public String detail;

        public ImageUrl(String url, String detail) {
            this.url = url;
            this.detail = detail;
        }
    }

    @SuppressWarnings({"unchecked", "rawTypes"})
    public static CompletionRequest convertRequest(MessageRequest messageRequest, boolean nativeSupport) {
        if (messageRequest == null) {
            return null;
        }

        CompletionRequest.CompletionRequestBuilder<?, ?> builder = CompletionRequest.builder();
        
        // 设置基本参数
        setBasicParameters(builder, messageRequest);
        
        // 处理消息转换
        List<Message> chatMessages = new ArrayList<>();
        processSystemMessage(messageRequest, chatMessages, nativeSupport);
        processInputMessages(messageRequest, chatMessages, nativeSupport);
        builder.messages(chatMessages);
        
        // 处理工具和工具选择
        setTools(builder, messageRequest);
        setToolChoice(builder, messageRequest);
        
        // 处理推理配置
        setThinkingConfig(builder, messageRequest, nativeSupport);

        return builder.build();
    }

    // 提取的辅助方法
    private static void setBasicParameters(CompletionRequest.CompletionRequestBuilder<?, ?> builder, MessageRequest request) {
        builder.model(request.getModel());
        
        if (request.getMaxTokens() != null) {
            builder.max_tokens(request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature().floatValue());
        }
        if (request.getTopP() != null) {
            builder.top_p(request.getTopP().floatValue());
        }
        
        // 处理停止序列
        if (request.getStopSequences() != null && !request.getStopSequences().isEmpty()) {
            if (request.getStopSequences().size() == 1) {
                builder.stop(request.getStopSequences().get(0));
            } else {
                builder.stop(request.getStopSequences());
            }
        }
        
        builder.stream(Boolean.TRUE.equals(request.getStream()));
    }

    private static void processSystemMessage(MessageRequest messageRequest, List<Message> chatMessages, boolean nativeSupport) {
        if (messageRequest.getSystem() == null) return;
        
        List<Map> contentParts = new ArrayList<>();
        if (messageRequest.getSystem() instanceof String) {
            contentParts.add(JacksonUtils.toMap(ContentPart.ofText((String) messageRequest.getSystem())));
        } else if (messageRequest.getSystem() instanceof List) {
            List<MessageRequest.RequestTextBlock> systemBlocks = (List<MessageRequest.RequestTextBlock>) messageRequest.getSystem();
            contentParts = systemBlocks.stream()
                    .map(systemBlock -> {
                        ContentPart contentPart = ContentPart.ofText(systemBlock.getText());
                        if(nativeSupport) {
                            contentPart.setCache_control(systemBlock.getCache_control());
                        }
                        return contentPart;
                    })
                    .map(JacksonUtils::toMap)
                    .collect(Collectors.toList());
        }
        
        if (!contentParts.isEmpty()) {
            chatMessages.add(Message.builder().role("system").content(contentParts).build());
        }
    }

    private static void processInputMessages(MessageRequest messageRequest, List<Message> chatMessages, boolean nativeSupport) {
        if (messageRequest.getMessages() == null) return;
        
        for (MessageRequest.InputMessage inputMessage : messageRequest.getMessages()) {
            List<Message> convertedMessages = convertSingleInputMessage(inputMessage, nativeSupport);
            chatMessages.addAll(convertedMessages);
        }
    }

    private static List<Message> convertSingleInputMessage(MessageRequest.InputMessage inputMessage, boolean nativeSupport) {
        Message.MessageBuilder chatMessageBuilder = Message.builder().role(inputMessage.getRole());
        Object contentObj = inputMessage.getContent();
        
        List<Map> contentParts = new ArrayList<>();
        List<Message.ToolCall> toolCalls = new ArrayList<>();
        List<Message.MessageBuilder> messageBuilders = new ArrayList<>();
        messageBuilders.add(chatMessageBuilder);

        if (contentObj instanceof String) {
            contentParts.add(JacksonUtils.toMap(ContentPart.ofText((String) contentObj)));
        } else if (contentObj instanceof List) {
            MessageProcessingContext context = new MessageProcessingContext(contentParts, toolCalls, messageBuilders, inputMessage.getRole());
            processContentBlocks((List<MessageRequest.ContentBlock>) contentObj, context, nativeSupport);
        }
        
        // 完成最后一个消息的构建
        finalizePendingMessage(messageBuilders, contentParts, toolCalls);
        
        return messageBuilders.stream()
                .map(Message.MessageBuilder::build)
                .collect(Collectors.toList());
    }

    private static void processContentBlocks(List<MessageRequest.ContentBlock> contentBlocks, 
                                           MessageProcessingContext context, boolean nativeSupport) {
        boolean lastToolResult = false;
        
        for (MessageRequest.ContentBlock block : contentBlocks) {
            if (lastToolResult) {
                context.getMessageBuilders().add(Message.builder().role(context.getOriginalRole()));
            }
            
            if (block instanceof MessageRequest.TextContentBlock) {
                lastToolResult = false;
                processTextContentBlock((MessageRequest.TextContentBlock) block, context, nativeSupport);
            } else if (block instanceof MessageRequest.ImageContentBlock) {
                lastToolResult = false;
                processImageContentBlock((MessageRequest.ImageContentBlock) block, context, nativeSupport);
            } else if (block instanceof MessageRequest.ToolUseContentBlock) {
                lastToolResult = false;
                processToolUseContentBlock((MessageRequest.ToolUseContentBlock) block, context, nativeSupport);
            } else if (block instanceof MessageRequest.ToolResultContentBlock) {
                lastToolResult = true;
                processToolResultContentBlock((MessageRequest.ToolResultContentBlock) block, context, nativeSupport);
            } else if (nativeSupport) {
                lastToolResult = false;
                processNativeContentBlock(block, context);
            }
        }
    }

    private static void processTextContentBlock(MessageRequest.TextContentBlock block, 
                                              MessageProcessingContext context, boolean nativeSupport) {
        ContentPart contentPart = ContentPart.ofText(block.getText());
        if (nativeSupport) {
            contentPart.setCache_control(block.getCache_control());
        }
        context.getContentParts().add(JacksonUtils.toMap(contentPart));
    }

    private static void processImageContentBlock(MessageRequest.ImageContentBlock block, 
                                               MessageProcessingContext context, boolean nativeSupport) {
        if (block.getSource() instanceof MessageRequest.Base64ImageSource) {
            MessageRequest.Base64ImageSource imageSource = (MessageRequest.Base64ImageSource) block.getSource();
            String dataUri = "data:" + imageSource.getMediaType() + ";base64," + imageSource.getData();
            ContentPart contentPart = ContentPart.ofImageUrl(new ImageUrl(dataUri, "auto"));
            if (nativeSupport) {
                contentPart.setCache_control(block.getCache_control());
            }
            context.getContentParts().add(JacksonUtils.toMap(contentPart));
        }
    }

    private static void processToolUseContentBlock(MessageRequest.ToolUseContentBlock block, 
                                                 MessageProcessingContext context, boolean nativeSupport) {
        Message.ToolCall toolCall = Message.ToolCall.fromFunctionIdAndName(block.getId(), block.getName());
        toolCall.setFunction(Message.FunctionCall.builder()
                .name(block.getName())
                .arguments(JacksonUtils.serialize(block.getInput()))
                .build());
        if (nativeSupport) {
            toolCall.setCache_control(block.getCache_control());
        }
        context.getToolCalls().add(toolCall);
    }

    private static void processToolResultContentBlock(MessageRequest.ToolResultContentBlock block, 
                                                    MessageProcessingContext context, boolean nativeSupport) {
        // Split tool result to different messages
        if (!context.getContentParts().isEmpty() || !context.getToolCalls().isEmpty()) {
            finalizePendingMessage(context.getMessageBuilders(), context.getContentParts(), context.getToolCalls());
            context.setContentParts(new ArrayList<>());
            context.setToolCalls(new ArrayList<>());
            context.getMessageBuilders().add(Message.builder());
        }
        
        Message.MessageBuilder current = context.getMessageBuilders().get(context.getMessageBuilders().size() - 1);
        current.tool_call_id(block.getToolUseId()).role("tool");
        
        String toolResultStringContent = extractToolResultStringContent(block);
        ContentPart contentPart = ContentPart.ofText(toolResultStringContent);
        if (block.getCache_control() != null && nativeSupport) {
            contentPart.setCache_control(block.getCache_control());
        }
        
        List<Map> toolResultContentParts = new ArrayList<>();
        toolResultContentParts.add(JacksonUtils.toMap(contentPart));
        current.content(toolResultContentParts);
    }

    private static void processNativeContentBlock(MessageRequest.ContentBlock block, MessageProcessingContext context) {
        // 包含thinking block和 redacted_thinking block
        if (context.getContentParts().isEmpty()) {
            context.getContentParts().add(JacksonUtils.toMap(block));
        } else if (block instanceof MessageRequest.ThinkingContentBlock) {
            context.getContentParts().add(0, JacksonUtils.toMap(block));
        } else if (block instanceof MessageRequest.RedactedThinkingContentBlock) {
            if ("thinking".equals(context.getContentParts().get(0).get("type"))) {
                context.getContentParts().add(1, JacksonUtils.toMap(block));
            } else {
                context.getContentParts().add(0, JacksonUtils.toMap(block));
            }
        }
    }

    private static String extractToolResultStringContent(MessageRequest.ToolResultContentBlock block) {
        if (block.getContent() instanceof String) {
            return (String) block.getContent();
        } else {
            return JacksonUtils.serialize(block.getContent());
        }
    }

    private static void finalizePendingMessage(List<Message.MessageBuilder> messageBuilders, 
                                             List<Map> contentParts, List<Message.ToolCall> toolCalls) {
        if (!contentParts.isEmpty()) {
            messageBuilders.get(messageBuilders.size() - 1).content(contentParts);
        }
        if (!toolCalls.isEmpty()) {
            messageBuilders.get(messageBuilders.size() - 1).tool_calls(toolCalls);
        }
    }

    private static void setTools(CompletionRequest.CompletionRequestBuilder<?, ?> builder, MessageRequest request) {
        if (request.getTools() == null || request.getTools().isEmpty()) return;
        
        List<Message.Tool> chatTools = new ArrayList<>();
        try {
            for (MessageRequest.Tool apiTool : request.getTools()) {
                Message.Tool chatTool = createChatTool(apiTool);
                chatTools.add(chatTool);
            }
            builder.tools(chatTools);
        } catch (Exception e) {
            log.info(e.getMessage());
        }
    }

    private static Message.Tool createChatTool(MessageRequest.Tool apiTool) {
        Message.Function.FunctionBuilder functionBuilder = Message.Function.builder()
                .name(apiTool.getName())
                .description(apiTool.getDescription());
        
        if (apiTool.getInputSchema() != null) {
            MessageRequest.InputSchema schema = apiTool.getInputSchema();
            Message.Function.FunctionParameter.FunctionParameterBuilder paramBuilder = Message.Function.FunctionParameter.builder()
                    .type(schema.getType())
                    .properties(schema.getProperties())
                    .required(schema.getRequired())
                    .additionalProperties(schema.isAdditionalProperties());
            functionBuilder.parameters(paramBuilder.build());
        }
        
        return Message.Tool.builder().type("function").function(functionBuilder.build()).build();
    }

    private static void setToolChoice(CompletionRequest.CompletionRequestBuilder<?, ?> builder, MessageRequest request) {
        if (request.getToolChoice() == null) return;
        
        MessageRequest.ToolChoice choice = request.getToolChoice();
        String choiceType = choice.getType();
        
        switch (choiceType) {
            case "auto":
            case "none":
                builder.tool_choice(choiceType);
                break;
            case "any":
                builder.tool_choice("auto");
                break;
            case "tool":
                if (choice instanceof MessageRequest.ToolChoiceTool) {
                    MessageRequest.ToolChoiceTool toolChoiceTool = (MessageRequest.ToolChoiceTool) choice;
                    Map<String, Object> toolChoiceMap = new HashMap<>();
                    toolChoiceMap.put("type", "function");
                    Map<String, String> functionDescMap = new HashMap<>();
                    functionDescMap.put("name", toolChoiceTool.getName());
                    toolChoiceMap.put("function", functionDescMap);
                    builder.tool_choice(toolChoiceMap);
                }
                break;
            default:
                builder.tool_choice(choiceType);
                break;
        }
    }

    private static void setThinkingConfig(CompletionRequest.CompletionRequestBuilder<?, ?> builder, 
                                        MessageRequest request, boolean nativeSupport) {
        if (request.getThinking() != null && request.getThinking() instanceof MessageRequest.ThinkingConfigEnabled) {
            if (nativeSupport) {
                builder.reasoning_effort(request.getThinking());
            } else {
                builder.reasoning_effort("medium");
            }
        }
    }

    // 辅助数据类
    @Data
    private static class MessageProcessingContext {
        private List<Map> contentParts;
        private List<Message.ToolCall> toolCalls;
        private List<Message.MessageBuilder> messageBuilders;
        private String originalRole;
        
        public MessageProcessingContext(List<Map> contentParts, List<Message.ToolCall> toolCalls, 
                                      List<Message.MessageBuilder> messageBuilders, String originalRole) {
            this.contentParts = contentParts;
            this.toolCalls = toolCalls;
            this.messageBuilders = messageBuilders;
            this.originalRole = originalRole;
        }
    }

    public static MessageResponse convertResponse(CompletionResponse chatResponse) {
        if (chatResponse == null || chatResponse.getChoices() == null || chatResponse.getChoices().isEmpty()) {
            return null;
        }

        MessageResponse.MessageResponseBuilder responseBuilder = MessageResponse.builder();
        responseBuilder.id(chatResponse.getId());
        responseBuilder.model(chatResponse.getModel());
        responseBuilder.type("message");
        responseBuilder.role("assistant");

        CompletionResponse.Choice choice = chatResponse.getChoices().get(0);
        Message assistantMessage = choice.getMessage();
        List<MessageResponse.ContentBlock> contentBlocks = new ArrayList<>();

        if(assistantMessage.getReasoning_content() != null || assistantMessage.getReasoning_content_signature() != null) {
            contentBlocks.add(new MessageResponse.ResponseThinkingBlock(assistantMessage.getReasoning_content(), assistantMessage.getReasoning_content_signature()));
        }

        if(assistantMessage.getRedacted_reasoning_content() != null) {
            contentBlocks.add(new MessageResponse.ResponseRedactedThinkingBlock(assistantMessage.getRedacted_reasoning_content()));
        }

        if (assistantMessage.getContent() != null) {
            String textContent = (String) assistantMessage.getContent();
            if (!textContent.isEmpty()) {
                 contentBlocks.add(new MessageResponse.ResponseTextBlock(textContent));
            }
        }

        if (assistantMessage.getTool_calls() != null && !assistantMessage.getTool_calls().isEmpty()) {
            for (Message.ToolCall toolCall : assistantMessage.getTool_calls()) {
                if ("function".equals(toolCall.getType())) {
                    Map<String, Object> parsedArgs = new HashMap<>();
                    if (toolCall.getFunction().getArguments() != null && !toolCall.getFunction().getArguments().isEmpty()) {
                        parsedArgs = JacksonUtils.deserialize(toolCall.getFunction().getArguments(), new TypeReference<Map<String, Object>>() {});
                    }
                    contentBlocks.add(new MessageResponse.ResponseToolUseBlock(toolCall.getId(), toolCall.getFunction().getName(), parsedArgs));
                }
            }
        }

        responseBuilder.content(contentBlocks);

        String finishReason = choice.getFinish_reason();
        String stopReason = mapFinishReason(finishReason);
        responseBuilder.stopReason(stopReason);
        responseBuilder.stopSequence(null);

        if (chatResponse.getUsage() != null) {
            CompletionResponse.TokenUsage sourceUsage = chatResponse.getUsage();
            MessageResponse.Usage usage = MessageResponse.Usage.builder()
                    .inputTokens(sourceUsage.getPrompt_tokens())
                    .outputTokens(sourceUsage.getCompletion_tokens())
                    .build();
            responseBuilder.usage(usage);
        }

        return responseBuilder.build();
    }

    public static List<StreamMessageResponse> convertStreamResponse(StreamCompletionResponse streamChatResponse, boolean isToolCall) {
        if (streamChatResponse == null) return null;
        List<StreamMessageResponse> responseList = new ArrayList<>();
        if (streamChatResponse.getError() != null) {
            responseList.add(StreamMessageResponse.error(streamChatResponse.getError().getType(), streamChatResponse.getError().getMessage()));
        }
        if(CollectionUtils.isNotEmpty(streamChatResponse.getChoices())) {
            StreamCompletionResponse.Choice streamChoice = streamChatResponse.getChoices().get(0);
            Message delta = streamChoice.getDelta();
            String finishReasonStr = streamChoice.getFinish_reason();
            int choiceIndex = streamChoice.getIndex(); // Typically 0 for non-parallel choices

            //Thinking content delta
            if(delta != null && (delta.getReasoning_content() != null)) {
                String reasoningContent = delta.getReasoning_content();
                if(!reasoningContent.isEmpty()) {
                    responseList.add(StreamMessageResponse.contentBlockDelta(choiceIndex, new StreamMessageResponse.ThinkingDelta(reasoningContent)));
                }
            }

            if(delta != null && (delta.getReasoning_content_signature() != null)) {
                String signature = delta.getReasoning_content_signature();
                if(!signature.isEmpty()) {
                    responseList.add(StreamMessageResponse.contentBlockDelta(choiceIndex, new StreamMessageResponse.SignatureDelta(signature)));
                }
            }

            if(delta != null && (delta.getRedacted_reasoning_content() != null)) {
                String redactedReasoningContent = delta.getRedacted_reasoning_content();
                if(!redactedReasoningContent.isEmpty()) {
                    responseList.add(StreamMessageResponse.contentBlockDelta(choiceIndex, new StreamMessageResponse.RedactedThinkingDelta(redactedReasoningContent)));
                }
            }


            // Text content delta
            if(delta != null && delta.getContent() != null) {
                String textContent = (String) delta.getContent();
                if(!textContent.isEmpty()) {
                    responseList.add(StreamMessageResponse.contentBlockDelta(choiceIndex, new StreamMessageResponse.TextDelta(textContent)));
                }
            }

            // Tool call delta (start or argument delta)
            if(delta != null && CollectionUtils.isNotEmpty(delta.getTool_calls())) {
                Message.ToolCall toolCallChunk = delta.getTool_calls().get(0);
                int toolContentBlockIndex = toolCallChunk.getIndex(); // This is the index for the content block (tool_use)

                if(toolCallChunk.getFunction() != null && StringUtils.isNotEmpty(toolCallChunk.getFunction().getName())) { // Start of a new tool call
                    responseList.add(StreamMessageResponse.contentBlockStart(toolContentBlockIndex,
                            new MessageResponse.ResponseToolUseBlock(toolCallChunk.getId(), toolCallChunk.getFunction().getName(), new HashMap<>())));
                }
                if(toolCallChunk.getFunction() != null && toolCallChunk.getFunction().getArguments() != null) { // Delta for arguments
                    responseList.add(StreamMessageResponse.contentBlockDelta(toolContentBlockIndex,
                            new StreamMessageResponse.InputJsonDelta(toolCallChunk.getFunction().getArguments())));
                }
            }
            // Finish reason
            if(finishReasonStr != null) {
                StreamMessageResponse.StreamUsage streamUsage = null;
                if(streamChatResponse.getUsage() != null) { // Full usage might be on the LAST chunk with finish_reason
                    streamUsage = StreamMessageResponse.StreamUsage.builder()
                            .outputTokens(streamChatResponse.getUsage().getCompletion_tokens())
                            .build();
                } else { // More typical for intermediate deltas, no usage reported per token
                    streamUsage = StreamMessageResponse.StreamUsage.builder().outputTokens(0).build();
                }

                String mappedStopReason = mapFinishReason(finishReasonStr);

                StreamMessageResponse.MessageDeltaInfo messageInfo = StreamMessageResponse.MessageDeltaInfo.builder()
                        .stopReason(mappedStopReason)
                        .build();

                responseList.add(StreamMessageResponse.messageDelta(messageInfo, streamUsage));
            }
        } else if (streamChatResponse.getUsage() != null) {
            StreamMessageResponse.StreamUsage streamUsage = StreamMessageResponse.StreamUsage.builder()
                    .outputTokens(streamChatResponse.getUsage().getCompletion_tokens())
                    .inputTokens(streamChatResponse.getUsage().getPrompt_tokens())
                    .build();
            StreamMessageResponse.MessageDeltaInfo messageInfo = StreamMessageResponse.MessageDeltaInfo.builder()
                    .stopReason(isToolCall ? "tool_use" : "end_turn")
                    .build();
            responseList.add(StreamMessageResponse.messageDelta(messageInfo, streamUsage));
        }

        return responseList;
    }

    private static String mapFinishReason(String finishReason) {
        if (finishReason == null) return null;
        switch (finishReason.toLowerCase()) {
            case "stop": return "end_turn";
            case "length": return "max_tokens";
            case "tool_calls":
            case "function_call":
                return "tool_use";
            case "content_filter": return "refusal";
            default: return finishReason;
        }
    }
}
