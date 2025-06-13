package com.ke.bella.openapi.protocol.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Slf4j
public class TransferUtils {

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

        builder.model(messageRequest.getModel());
        if (messageRequest.getMaxTokens() != null) {
            builder.max_tokens(messageRequest.getMaxTokens());
        }
        if (messageRequest.getTemperature() != null) {
            builder.temperature(messageRequest.getTemperature().floatValue());
        }
        if (messageRequest.getTopP() != null) {
            builder.top_p(messageRequest.getTopP().floatValue());
        }
        if (messageRequest.getStopSequences() != null && !messageRequest.getStopSequences().isEmpty()) {
            if (messageRequest.getStopSequences().size() == 1) {
                builder.stop(messageRequest.getStopSequences().get(0));
            } else {
                builder.stop(messageRequest.getStopSequences());
            }
        }
        builder.stream(messageRequest.isStream());

        List<Message> chatMessages = new ArrayList<>();
        if (messageRequest.getSystem() != null) {
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

        if (messageRequest.getMessages() != null) {
            for (MessageRequest.InputMessage inputMessage : messageRequest.getMessages()) {

                Message.MessageBuilder chatMessageBuilder = Message.builder();
                String role = inputMessage.getRole();
                chatMessageBuilder.role(role);
                Object contentObj = inputMessage.getContent();
                List<Map> contentParts = new ArrayList<>();
                List<Message.ToolCall> toolCalls = new ArrayList<>();

                List<Message.MessageBuilder> messageBuilders = new ArrayList<>();
                messageBuilders.add(chatMessageBuilder);

                if (contentObj instanceof String) {
                    contentParts.add(JacksonUtils.toMap(ContentPart.ofText((String) contentObj)));
                } else if (contentObj instanceof List) {
                    List<MessageRequest.ContentBlock> contentBlocks = (List<MessageRequest.ContentBlock>) contentObj;
                    for (MessageRequest.ContentBlock block : contentBlocks) {
                        if (block instanceof MessageRequest.TextContentBlock) {
                            ContentPart contentPart = ContentPart.ofText(((MessageRequest.TextContentBlock) block).getText());
                            if(nativeSupport) {
                                contentPart.setCache_control(block.getCache_control());
                            }
                            contentParts.add(JacksonUtils.toMap(contentPart));
                        } else if (block instanceof MessageRequest.ImageContentBlock) {
                            MessageRequest.ImageContentBlock imageBlock = (MessageRequest.ImageContentBlock) block;
                            if (imageBlock.getSource() instanceof MessageRequest.Base64ImageSource) {
                                MessageRequest.Base64ImageSource imageSource = (MessageRequest.Base64ImageSource) imageBlock.getSource();
                                String dataUri = "data:" + imageSource.getMediaType() + ";base64," + imageSource.getData();
                                ContentPart contentPart = ContentPart.ofImageUrl(new ImageUrl(dataUri, "auto"));
                                if(nativeSupport) {
                                    contentPart.setCache_control(block.getCache_control());
                                }
                                contentParts.add(JacksonUtils.toMap(contentPart));
                            }
                        } else if(block instanceof MessageRequest.ToolUseContentBlock) {
                            MessageRequest.ToolUseContentBlock toolUseContentBlock = (MessageRequest.ToolUseContentBlock) block;
                            Message.ToolCall toolCall = Message.ToolCall.fromFunctionIdAndName(toolUseContentBlock.getId(), toolUseContentBlock.getName());
                            toolCall.setFunction(Message.FunctionCall.builder()
                                            .name(toolUseContentBlock.getName())
                                            .arguments(JacksonUtils.serialize(toolUseContentBlock.getInput()))
                                            .build());
                            toolCalls.add(toolCall);
                        } else if (block instanceof MessageRequest.ToolResultContentBlock) {
                            // Split tool result to different messages.
                            // Firstly store previous content to current message, and clear the content parts.
                            if(!contentParts.isEmpty() || !toolCalls.isEmpty()) {
                                if (!contentParts.isEmpty()) {
                                    messageBuilders.get(messageBuilders.size() - 1).content(contentParts);
                                }
                                if (!toolCalls.isEmpty()) {
                                    messageBuilders.get(messageBuilders.size() - 1).tool_calls(toolCalls);
                                }
                                Message.MessageBuilder additional = Message.builder();
                                messageBuilders.add(additional);
                                contentParts.clear();
                                toolCalls.clear();
                            }
                            Message.MessageBuilder current = messageBuilders.get(messageBuilders.size() - 1);
                            // This assumes ToolResultContentBlock's content should also be part of the main 'content' field
                            // when role is 'tool'. The content itself should be a string for a text part.
                            MessageRequest.ToolResultContentBlock toolResultBlock = (MessageRequest.ToolResultContentBlock) block;
                            current.tool_call_id(toolResultBlock.getToolUseId());
                            current.role("tool");
                            String toolResultStringContent = "";
                            if (toolResultBlock.getContent() instanceof String) {
                                toolResultStringContent = (String) toolResultBlock.getContent();
                            } else if (toolResultBlock.getContent() != null) {
                                toolResultStringContent = JacksonUtils.serialize(toolResultBlock.getContent());
                            }
                            contentParts.add(JacksonUtils.toMap(ContentPart.ofText(toolResultStringContent)));
                        } else if(nativeSupport) {
                            //包含thinking block和 redacted_thinking block
                            contentParts.add(JacksonUtils.toMap(block));
                        }
                    }
                }
                if (!contentParts.isEmpty()) {
                    messageBuilders.get(messageBuilders.size() - 1).content(contentParts);
                }
                if (!toolCalls.isEmpty()) {
                    messageBuilders.get(messageBuilders.size() - 1).tool_calls(toolCalls);
                }

                messageBuilders.forEach(messageBuilder -> chatMessages.add(messageBuilder.build()));
            }
        }
        builder.messages(chatMessages);

        if (messageRequest.getTools() != null && !messageRequest.getTools().isEmpty()) {
            List<Message.Tool> chatTools = new ArrayList<>();
            try {
                for (MessageRequest.Tool apiTool : messageRequest.getTools()) {
                    Message.Function.FunctionBuilder functionBuilder = Message.Function.builder()
                            .name(apiTool.getName())
                            .description(apiTool.getDescription());
                    if(apiTool.getInputSchema() != null) {
                        MessageRequest.InputSchema schema = apiTool.getInputSchema();
                        Message.Function.FunctionParameter.FunctionParameterBuilder paramBuilder = Message.Function.FunctionParameter.builder()
                                .type(schema.getType())
                                .properties(schema.getProperties())
                                .required(schema.getRequired())
                                .additionalProperties(schema.isAdditionalProperties());
                        functionBuilder.parameters(paramBuilder.build());
                    }
                    chatTools.add(Message.Tool.builder().type("function").function(functionBuilder.build()).build());
                }
                builder.tools(chatTools);
            } catch (Exception e) {
                LOGGER.info(e.getMessage());
            }
        }

        if (messageRequest.getToolChoice() != null) {
            MessageRequest.ToolChoice choice = messageRequest.getToolChoice();
            String choiceType = choice.getType(); // Common field
            switch (choiceType) {
                case "auto":
                case "none":
                    builder.tool_choice(choiceType);
                    break;
                case "any":
                    builder.tool_choice("auto");
                    break;
                case "tool": // This corresponds to ToolChoiceTool
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
                    // If it's a string type not explicitly handled, pass it through
                    builder.tool_choice(choiceType);
                    break;
            }
        }

        if (messageRequest.getThinking() != null) {
            if (messageRequest.getThinking() instanceof MessageRequest.ThinkingConfigEnabled) {
                if(nativeSupport) {
                    builder.reasoning_effort(messageRequest.getThinking());
                } else {
                    builder.reasoning_effort("medium");
                }
            }
        }

        return builder.build();
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

        if(assistantMessage.getReasoning_content() != null || assistantMessage.getReasoning_content_signature() != null) {
            contentBlocks.add(new MessageResponse.ResponseThinkingBlock(assistantMessage.getReasoning_content(), assistantMessage.getReasoning_content_signature()));
        }

        if(assistantMessage.getRedacted_reasoning_content() != null) {
            contentBlocks.add(new MessageResponse.ResponseRedactedThinkingBlock(assistantMessage.getRedacted_reasoning_content()));
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

                if(toolCallChunk.getFunction() != null && toolCallChunk.getFunction().getName() != null) { // Start of a new tool call
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
