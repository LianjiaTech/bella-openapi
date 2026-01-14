package com.ke.bella.openapi.protocol.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.ke.bella.openapi.protocol.message.MessageRequest.CacheControl.convertToCacheControl;

@Slf4j
public class TransferToCompletionsUtils {

    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    static class ContentPart {
        public String type;
        public String text;

        public static ContentPart ofText(String text) {
            return new ContentPart("text", text);
        }
    }

    public static MessageRequest convertRequest(CompletionRequest completionRequest) {
        if(completionRequest == null) {
            return null;
        }

        MessageRequest.MessageRequestBuilder builder = MessageRequest.builder();

        // 设置基本参数
        setBasicParameters(builder, completionRequest);

        // 处理消息转换
        MessageConversionResult result = convertMessages(completionRequest.getMessages());
        builder.messages(result.getMessages());
        builder.system(result.getSystemContent());

        // 处理工具和工具选择
        setTools(builder, completionRequest);
        setToolChoice(builder, completionRequest);

        // 处理推理配置
        setThinkingConfig(builder, completionRequest);

        return builder.build();
    }

    // 提取的辅助方法
    private static void setBasicParameters(MessageRequest.MessageRequestBuilder builder, CompletionRequest request) {
        builder.model(request.getModel());

        if(request.getMax_tokens() != null) {
            builder.maxTokens(request.getMax_tokens());
        }
        if(request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if(request.getTop_p() != null) {
            builder.topP(request.getTop_p());
        }

        // 处理停止序列
        if(request.getStop() != null) {
            List<String> stopList = request.getStop() instanceof String
                    ? Collections.singletonList((String) request.getStop())
                    : (List<String>) request.getStop();
            builder.stopSequences(stopList);
        }

        builder.stream(request.isStream());
    }

    private static MessageConversionResult convertMessages(List<Message> messages) {
        MessageConversionResult result = new MessageConversionResult();

        if(messages == null) {
            return result;
        }

        for (Message message : messages) {
            if("system".equals(message.getRole())) {
                result.setSystemContent(convertContentToMessageFormat(message.getContent()));
            } else if("tool".equals(message.getRole())) {
                // Tool messages should be merged with the previous user message
                if(!result.getMessages().isEmpty()) {
                    MessageRequest.InputMessage lastMessage = result.getMessages().get(result.getMessages().size() - 1);
                    if("user".equals(lastMessage.getRole())) {
                        // Merge tool result into the last user message
                        MessageRequest.InputMessage mergedMessage = mergeToolResultIntoAssistantMessage(lastMessage, message);
                        result.getMessages().set(result.getMessages().size() - 1, mergedMessage);
                    } else {
                        // If previous message is not user, convert normally
                        // (fallback)
                        MessageRequest.InputMessage convertedMessage = convertSingleMessage(message);
                        result.addMessage(convertedMessage);
                    }
                } else {
                    // If no previous message, convert normally (fallback)
                    MessageRequest.InputMessage convertedMessage = convertSingleMessage(message);
                    result.addMessage(convertedMessage);
                }
            } else {
                MessageRequest.InputMessage convertedMessage = convertSingleMessage(message);
                result.addMessage(convertedMessage);
            }
        }
        mergeMessages(result.getMessages());
        return result;
    }

    private static void mergeMessages(List<MessageRequest.InputMessage> messages) {
        if(messages == null || messages.size() <= 1) {
            return;
        }

        List<MessageRequest.InputMessage> mergedMessages = new ArrayList<>();
        MessageRequest.InputMessage currentMessage = null;

        for (MessageRequest.InputMessage message : messages) {
            if(currentMessage == null) {
                currentMessage = message;
            } else if("user".equals(currentMessage.getRole()) && "user".equals(message.getRole())) {
                // Merge consecutive user messages
                currentMessage = mergeUserMessages(currentMessage, message);
            } else {
                // Different roles or assistant message, add current and start
                // new
                mergedMessages.add(currentMessage);
                currentMessage = message;
            }
        }

        // Add the last message
        if(currentMessage != null) {
            mergedMessages.add(currentMessage);
        }

        // Replace original messages with merged ones
        messages.clear();
        messages.addAll(mergedMessages);
    }

    private static MessageRequest.InputMessage mergeUserMessages(MessageRequest.InputMessage first, MessageRequest.InputMessage second) {
        List<MessageRequest.ContentBlock> mergedContentBlocks = new ArrayList<>();

        // Add content from first message
        addContentToBlocks(first.getContent(), mergedContentBlocks);

        // Add content from second message
        addContentToBlocks(second.getContent(), mergedContentBlocks);

        // Move all ToolResultContentBlock to the front
        List<MessageRequest.ContentBlock> toolResultBlocks = new ArrayList<>();
        List<MessageRequest.ContentBlock> otherBlocks = new ArrayList<>();

        for (MessageRequest.ContentBlock block : mergedContentBlocks) {
            if(block instanceof MessageRequest.ToolResultContentBlock) {
                toolResultBlocks.add(block);
            } else {
                otherBlocks.add(block);
            }
        }

        // Combine: tool results first, then other blocks
        List<MessageRequest.ContentBlock> reorderedBlocks = new ArrayList<>();
        reorderedBlocks.addAll(toolResultBlocks);
        reorderedBlocks.addAll(otherBlocks);

        // Create merged message
        MessageRequest.InputMessage.InputMessageBuilder messageBuilder = MessageRequest.InputMessage.builder();
        messageBuilder.role("user");

        // Set merged content
        if(reorderedBlocks.size() == 1 && reorderedBlocks.get(0) instanceof MessageRequest.TextContentBlock) {
            messageBuilder.content(((MessageRequest.TextContentBlock) reorderedBlocks.get(0)).getText());
        } else if(!reorderedBlocks.isEmpty()) {
            messageBuilder.content(reorderedBlocks);
        }

        return messageBuilder.build();
    }

    private static void addContentToBlocks(Object content, List<MessageRequest.ContentBlock> contentBlocks) {
        if(content == null)
            return;

        if(content instanceof String) {
            MessageRequest.TextContentBlock textBlock = new MessageRequest.TextContentBlock();
            textBlock.setText((String) content);
            contentBlocks.add(textBlock);
        } else if(content instanceof List) {
            @SuppressWarnings("unchecked")
            List<MessageRequest.ContentBlock> blocks = (List<MessageRequest.ContentBlock>) content;
            contentBlocks.addAll(blocks);
        }
    }

    @SuppressWarnings("unchecked")
    private static MessageRequest.InputMessage mergeToolResultIntoAssistantMessage(MessageRequest.InputMessage userMessage, Message toolMessage) {
        // Create a new list of content blocks based on the existing user
        // message
        List<MessageRequest.ContentBlock> contentBlocks = new ArrayList<>();

        // Add existing content from user message
        if(userMessage.getContent() instanceof String) {
            MessageRequest.TextContentBlock textBlock = new MessageRequest.TextContentBlock();
            textBlock.setText((String) userMessage.getContent());
            contentBlocks.add(textBlock);
        } else if(userMessage.getContent() instanceof List) {
            contentBlocks.addAll((List<MessageRequest.ContentBlock>) userMessage.getContent());
        }

        // Add tool result content block
        if(toolMessage.getTool_call_id() != null) {
            MessageRequest.ToolResultContentBlock toolResultBlock = new MessageRequest.ToolResultContentBlock();
            toolResultBlock.setToolUseId(toolMessage.getTool_call_id());
            toolResultBlock.setContent(extractToolResultContent(toolMessage));
            contentBlocks.add(toolResultBlock);
        }

        // Create new message with merged content
        MessageRequest.InputMessage.InputMessageBuilder messageBuilder = MessageRequest.InputMessage.builder();
        messageBuilder.role(userMessage.getRole());

        // Set merged content
        if(contentBlocks.size() == 1 && contentBlocks.get(0) instanceof MessageRequest.TextContentBlock) {
            messageBuilder.content(((MessageRequest.TextContentBlock) contentBlocks.get(0)).getText());
        } else if(!contentBlocks.isEmpty()) {
            messageBuilder.content(contentBlocks);
        }

        return messageBuilder.build();
    }

    private static MessageRequest.InputMessage convertSingleMessage(Message message) {
        MessageRequest.InputMessage.InputMessageBuilder messageBuilder = MessageRequest.InputMessage.builder();
        messageBuilder.role(message.getRole().equals("assistant") ? "assistant" : "user");

        List<MessageRequest.ContentBlock> contentBlocks = new ArrayList<>();

        // 处理thinking内容
        processThinkContent(message, contentBlocks);

        // 处理文本内容
        processTextContent(message, contentBlocks);

        // 处理工具调用
        processToolCalls(message, contentBlocks);

        // 处理工具调用结果
        processToolResults(message, contentBlocks);

        // 设置最终内容
        setMessageContent(messageBuilder, contentBlocks);

        return messageBuilder.build();
    }

    private static void processThinkContent(Message message, List<MessageRequest.ContentBlock> contentBlocks) {
        if(message.getReasoning_content() != null) {
            MessageRequest.ThinkingContentBlock thinkingContentBlock = new MessageRequest.ThinkingContentBlock();
            thinkingContentBlock.setThinking(message.getReasoning_content());
            thinkingContentBlock.setSignature(message.getReasoning_content_signature());
            contentBlocks.add(thinkingContentBlock);
        }
        if(message.getRedacted_reasoning_content() != null) {
            MessageRequest.RedactedThinkingContentBlock redactedThinkingContentBlock = new MessageRequest.RedactedThinkingContentBlock();
            redactedThinkingContentBlock.setData(message.getRedacted_reasoning_content());
            contentBlocks.add(redactedThinkingContentBlock);
        }
    }

    @SuppressWarnings("unchecked")
    private static void processTextContent(Message message, List<MessageRequest.ContentBlock> contentBlocks) {
        if(message.getContent() == null || "tool".equals(message.getRole()))
            return;

        if(message.getContent() instanceof String) {
            MessageRequest.TextContentBlock textBlock = new MessageRequest.TextContentBlock();
            textBlock.setText((String) message.getContent());
            contentBlocks.add(textBlock);
        } else if(message.getContent() instanceof List) {
            List<Map> contentParts = (List<Map>) message.getContent();
            for (Map part : contentParts) {
                MessageRequest.ContentBlock block = convertContentPartToBlock(part);
                if(block != null) {
                    contentBlocks.add(block);
                }
            }
        }
    }

    private static void processToolCalls(Message message, List<MessageRequest.ContentBlock> contentBlocks) {
        if(message.getTool_calls() == null || message.getTool_calls().isEmpty())
            return;

        for (Message.ToolCall toolCall : message.getTool_calls()) {
            if("function".equals(toolCall.getType())) {
                MessageRequest.ToolUseContentBlock toolUseBlock = createToolUseBlock(toolCall);
                contentBlocks.add(toolUseBlock);
            }
        }
    }

    private static void processToolResults(Message message, List<MessageRequest.ContentBlock> contentBlocks) {
        if(!"tool".equals(message.getRole()) || message.getTool_call_id() == null)
            return;

        MessageRequest.ToolResultContentBlock toolResultBlock = new MessageRequest.ToolResultContentBlock();
        toolResultBlock.setToolUseId(message.getTool_call_id());
        toolResultBlock.setContent(extractToolResultContent(message));
        contentBlocks.add(toolResultBlock);
    }

    private static MessageRequest.ToolUseContentBlock createToolUseBlock(Message.ToolCall toolCall) {
        MessageRequest.ToolUseContentBlock toolUseBlock = new MessageRequest.ToolUseContentBlock();
        toolUseBlock.setId(toolCall.getId());
        toolUseBlock.setName(toolCall.getFunction().getName());

        if(toolCall.getFunction().getArguments() != null) {
            Map<String, Object> args = JacksonUtils.deserialize(
                    toolCall.getFunction().getArguments(),
                    new TypeReference<Map<String, Object>>() {
                    });
            if(args == null) {
                args = new HashMap<>();
            }
            toolUseBlock.setInput(args);
        }

        return toolUseBlock;
    }

    @SuppressWarnings("unchecked")
    private static Object extractToolResultContent(Message message) {
        if(message.getContent() instanceof String) {
            return message.getContent();
        } else if(message.getContent() instanceof List) {
            List<Map> contentParts = (List<Map>) message.getContent();
            if(!contentParts.isEmpty() && "text".equals(contentParts.get(0).get("type"))) {
                return contentParts.get(0).get("text");
            }
        }
        return null;
    }

    private static void setMessageContent(MessageRequest.InputMessage.InputMessageBuilder messageBuilder,
            List<MessageRequest.ContentBlock> contentBlocks) {
        if(contentBlocks.size() == 1 && contentBlocks.get(0) instanceof MessageRequest.TextContentBlock) {
            messageBuilder.content(((MessageRequest.TextContentBlock) contentBlocks.get(0)).getText());
        } else if(!contentBlocks.isEmpty()) {
            messageBuilder.content(contentBlocks);
        }
    }

    private static void setTools(MessageRequest.MessageRequestBuilder builder, CompletionRequest request) {
        if(request.getTools() == null || request.getTools().isEmpty())
            return;

        List<MessageRequest.Tool> messageTools = new ArrayList<>();
        for (Message.Tool tool : request.getTools()) {
            if("function".equals(tool.getType()) && tool.getFunction() != null) {
                MessageRequest.Tool messageTool = createMessageTool(tool);
                messageTools.add(messageTool);
            }
        }
        builder.tools(messageTools);
    }

    private static MessageRequest.Tool createMessageTool(Message.Tool tool) {
        MessageRequest.Tool.ToolBuilder toolBuilder = MessageRequest.Tool.builder()
                .name(tool.getFunction().getName())
                .description(tool.getFunction().getDescription())
                .cache_control(convertToCacheControl(tool.getCache_control()));

        if(tool.getFunction().getParameters() != null) {
            Message.Function.FunctionParameter params = tool.getFunction().getParameters();
            MessageRequest.InputSchema.InputSchemaBuilder schemaBuilder = MessageRequest.InputSchema.builder()
                    .type(params.getType())
                    .additionalProperties(params.getAdditionalProperties()); // Keep
                                                                             // as
                                                                             // Object
                                                                             // (Boolean
                                                                             // or
                                                                             // schema)

            if(params.getProperties() != null) {
                schemaBuilder.properties(params.getProperties());
            }
            if(params.getRequired() != null) {
                schemaBuilder.required(params.getRequired());
            }

            toolBuilder.inputSchema(schemaBuilder.build());
        }

        return toolBuilder.build();
    }

    private static void setToolChoice(MessageRequest.MessageRequestBuilder builder, CompletionRequest request) {
        if(request.getTool_choice() != null) {
            MessageRequest.ToolChoice toolChoice = convertToolChoice(request.getTool_choice());
            builder.toolChoice(toolChoice);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setThinkingConfig(MessageRequest.MessageRequestBuilder builder, CompletionRequest request) {
        if(request.getReasoning_effort() != null) {
            if(request.getReasoning_effort() instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) request.getReasoning_effort();
                if(map.containsKey("budget_tokens") && map.get("budget_tokens") instanceof Integer) {
                    builder.thinking(new MessageRequest.ThinkingConfigEnabled((Integer) map.get("budget_tokens")));
                }
            } else if("low".equals(request.getReasoning_effort())) {
                builder.thinking(new MessageRequest.ThinkingConfigEnabled(4000));
            } else if("medium".equals(request.getReasoning_effort())) {
                builder.thinking(new MessageRequest.ThinkingConfigEnabled(2000));
            } else if("high".equals(request.getReasoning_effort())) {
                builder.thinking(new MessageRequest.ThinkingConfigEnabled(8000));
            }
        }
    }

    // 辅助数据类
    @Data
    private static class MessageConversionResult {
        private List<MessageRequest.InputMessage> messages = new ArrayList<>();
        private Object systemContent;

        public void addMessage(MessageRequest.InputMessage message) {
            messages.add(message);
        }
    }

    public static CompletionResponse convertResponse(MessageResponse messageResponse) {
        if(messageResponse == null) {
            return null;
        }

        CompletionResponse.CompletionResponseBuilder<?, ?> responseBuilder = CompletionResponse.builder();
        responseBuilder.id(messageResponse.getId());
        responseBuilder.model(messageResponse.getModel());
        responseBuilder.object("chat.completion");

        // Create choice with message
        Message.MessageBuilder messageBuilder = Message.builder();
        messageBuilder.role(messageResponse.getRole());

        List<ContentPart> textContents = new ArrayList<>();
        List<Message.ToolCall> toolCalls = new ArrayList<>();
        String reasoningContent = null;
        String reasoningSignature = null;
        String redactedReasoningContent = null;

        if(messageResponse.getContent() != null) {
            for (MessageResponse.ContentBlock block : messageResponse.getContent()) {
                if(block instanceof MessageResponse.ResponseTextBlock) {
                    MessageResponse.ResponseTextBlock textBlock = (MessageResponse.ResponseTextBlock) block;
                    if(textBlock.getText() != null) {
                        textContents.add(ContentPart.ofText(textBlock.getText()));
                    }
                } else if(block instanceof MessageResponse.ResponseToolUseBlock) {
                    MessageResponse.ResponseToolUseBlock toolBlock = (MessageResponse.ResponseToolUseBlock) block;
                    String args = JacksonUtils.serialize(toolBlock.getInput());
                    Message.ToolCall toolCall = Message.ToolCall.fromFunctionIdAndName(toolBlock.getId(), toolBlock.getName());
                    toolCall.setFunction(Message.FunctionCall.builder()
                            .name(toolBlock.getName())
                            .arguments(args == null ? "" : args)
                            .build());
                    toolCalls.add(toolCall);
                } else if(block instanceof MessageResponse.ResponseThinkingBlock) {
                    MessageResponse.ResponseThinkingBlock thinkingBlock = (MessageResponse.ResponseThinkingBlock) block;
                    reasoningContent = thinkingBlock.getThinking();
                    reasoningSignature = thinkingBlock.getSignature();
                } else if(block instanceof MessageResponse.ResponseRedactedThinkingBlock) {
                    MessageResponse.ResponseRedactedThinkingBlock redactedBlock = (MessageResponse.ResponseRedactedThinkingBlock) block;
                    redactedReasoningContent = redactedBlock.getData();
                }
            }
        }

        if(!textContents.isEmpty()) {
            messageBuilder.content(textContents.stream().map(ContentPart::getText).reduce((s1, s2) -> s1 + "\n" + s2).get());
        }
        if(!toolCalls.isEmpty()) {
            messageBuilder.tool_calls(toolCalls);
        }
        if(reasoningContent != null) {
            messageBuilder.reasoning_content(reasoningContent);
        }
        if(reasoningSignature != null) {
            messageBuilder.reasoning_content_signature(reasoningSignature);
        }
        if(redactedReasoningContent != null) {
            messageBuilder.redacted_reasoning_content(redactedReasoningContent);
        }

        CompletionResponse.Choice choice = CompletionResponse.Choice.builder()
                .index(0)
                .message(messageBuilder.build())
                .finish_reason(mapStopReasonToFinish(messageResponse.getStopReason()))
                .build();

        responseBuilder.choices(Collections.singletonList(choice));

        if(messageResponse.getUsage() != null) {
            CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
            usage.setCache_creation_tokens(messageResponse.getUsage().getCacheCreationInputTokens());
            usage.setCache_read_tokens(messageResponse.getUsage().getCacheReadInputTokens());
            if(usage.getCache_creation_tokens() > 0 || usage.getCache_read_tokens() > 0) {
                CompletionResponse.TokensDetail tokensDetail = new CompletionResponse.TokensDetail();
                tokensDetail.setCached_tokens(usage.getCache_read_tokens());
                tokensDetail.setCache_creation_tokens(usage.getCache_creation_tokens());
                usage.setPrompt_tokens_details(tokensDetail);
            }
            usage.setPrompt_tokens(messageResponse.getUsage().getInputTokens());
            usage.setCompletion_tokens(messageResponse.getUsage().getOutputTokens());
            usage.setTotal_tokens(messageResponse.getUsage().getInputTokens() + messageResponse.getUsage().getOutputTokens());
            responseBuilder.usage(usage);
        }

        return responseBuilder.build();
    }

    public static StreamCompletionResponse convertStreamResponse(StreamMessageResponse streamMessageResponse,
            String model, String id, AtomicInteger toolNum, MessageResponse.Usage tokenUsage) {
        if(streamMessageResponse == null) {
            return null;
        }

        StreamCompletionResponse.StreamCompletionResponseBuilder<?, ?> responseBuilder = StreamCompletionResponse.builder();

        responseBuilder.object("chat.completion.chunk");

        responseBuilder.model(model);

        responseBuilder.id(id);

        responseBuilder.created(DateTimeUtils.getCurrentSeconds());

        if(streamMessageResponse.getError() != null) {
            return responseBuilder.error(OpenapiResponse.OpenapiError.builder()
                    .type(streamMessageResponse.getError().getType())
                    .message(streamMessageResponse.getError().getMessage())
                    .code(streamMessageResponse.getError().getType()).build()).build();
        }

        // Handle different stream message types
        String type = streamMessageResponse.getType();

        if("content_block_delta".equals(type)) {
            Message.MessageBuilder deltaBuilder = Message.builder();
            StreamMessageResponse.Delta delta = createDeltaFromMap(streamMessageResponse.getDelta());
            if(delta instanceof StreamMessageResponse.TextDelta) {
                StreamMessageResponse.TextDelta textDelta = (StreamMessageResponse.TextDelta) delta;
                deltaBuilder.content(textDelta.getText());
            } else if(delta instanceof StreamMessageResponse.ThinkingDelta) {
                StreamMessageResponse.ThinkingDelta thinkingDelta = (StreamMessageResponse.ThinkingDelta) delta;
                deltaBuilder.reasoning_content(thinkingDelta.getThinking());
            } else if(delta instanceof StreamMessageResponse.SignatureDelta) {
                StreamMessageResponse.SignatureDelta signatureDelta = (StreamMessageResponse.SignatureDelta) delta;
                deltaBuilder.reasoning_content_signature(signatureDelta.getSignature());
            } else if(delta instanceof StreamMessageResponse.RedactedThinkingDelta) {
                StreamMessageResponse.RedactedThinkingDelta redactedDelta = (StreamMessageResponse.RedactedThinkingDelta) delta;
                deltaBuilder.redacted_reasoning_content(redactedDelta.getData());
            } else if(delta instanceof StreamMessageResponse.InputJsonDelta) {
                StreamMessageResponse.InputJsonDelta jsonDelta = (StreamMessageResponse.InputJsonDelta) delta;
                Message.ToolCall toolCall = Message.ToolCall.builder()
                        .index(Math.max(0, toolNum.get() - 1))
                        .function(Message.FunctionCall.builder()
                                .arguments(jsonDelta.getPartialJson() == null ? "" : jsonDelta.getPartialJson())
                                .build())
                        .build();
                deltaBuilder.tool_calls(Collections.singletonList(toolCall));
            }

            StreamCompletionResponse.Choice choice = StreamCompletionResponse.Choice.builder()
                    .index(streamMessageResponse.getIndex() != null ? streamMessageResponse.getIndex() : 0)
                    .delta(deltaBuilder.build())
                    .build();

            responseBuilder.choices(Collections.singletonList(choice));

        } else if("content_block_start".equals(type)) {
            Message.MessageBuilder deltaBuilder = Message.builder();

            if(streamMessageResponse.getContentBlock() instanceof MessageResponse.ResponseToolUseBlock) {
                toolNum.incrementAndGet();
                MessageResponse.ResponseToolUseBlock toolBlock = (MessageResponse.ResponseToolUseBlock) streamMessageResponse.getContentBlock();
                Message.ToolCall toolCall = Message.ToolCall.builder()
                        .index(Math.max(0, toolNum.get() - 1))
                        .id(toolBlock.getId())
                        .type("function")
                        .function(Message.FunctionCall.builder()
                                .name(toolBlock.getName())
                                .build())
                        .build();
                deltaBuilder.tool_calls(Collections.singletonList(toolCall));
            } else {
                return null;
            }

            StreamCompletionResponse.Choice choice = StreamCompletionResponse.Choice.builder()
                    .index(0)
                    .delta(deltaBuilder.build())
                    .build();

            responseBuilder.choices(Collections.singletonList(choice));

        } else if("message_delta".equals(type)) {
            StreamMessageResponse.MessageDeltaInfo delta = createMessageDeltaInfoFromMap(streamMessageResponse.getDelta());
            if(delta != null) {
                StreamCompletionResponse.Choice choice = StreamCompletionResponse.Choice.builder()
                        .index(0)
                        .finish_reason(mapStopReasonToFinish(delta.getStopReason()))
                        .delta(Message.builder().build())
                        .build();
                responseBuilder.choices(Collections.singletonList(choice));
            }
            if(streamMessageResponse.getUsage() != null) {
                CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
                usage.setPrompt_tokens(streamMessageResponse.getUsage().getInputTokens());
                usage.setCompletion_tokens(streamMessageResponse.getUsage().getOutputTokens());
                usage.setCache_creation_tokens(streamMessageResponse.getUsage().getCacheCreationInputTokens());
                usage.setCache_read_tokens(streamMessageResponse.getUsage().getCacheReadInputTokens());
                if(tokenUsage != null) {
                    usage.setCache_creation_tokens(tokenUsage.getCacheCreationInputTokens() + usage.getCache_creation_tokens());
                    usage.setCache_read_tokens(tokenUsage.getCacheReadInputTokens() + usage.getCache_read_tokens());
                    usage.setPrompt_tokens(tokenUsage.getInputTokens() + usage.getPrompt_tokens());
                    usage.setCompletion_tokens(tokenUsage.getOutputTokens() + usage.getCompletion_tokens());
                }
                if(usage.getCache_creation_tokens() > 0 || usage.getCache_read_tokens() > 0) {
                    CompletionResponse.TokensDetail tokensDetail = new CompletionResponse.TokensDetail();
                    tokensDetail.setCached_tokens(usage.getCache_read_tokens());
                    tokensDetail.setCache_creation_tokens(usage.getCache_creation_tokens());
                    usage.setPrompt_tokens_details(tokensDetail);
                }
                usage.setTotal_tokens(usage.getPrompt_tokens() + usage.getCompletion_tokens());
                responseBuilder.usage(usage);
            }
        } else {
            return null;
        }

        return responseBuilder.build();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object convertContentToMessageFormat(Object content) {
        if(content instanceof String) {
            return content;
        } else if(content instanceof List) {
            List<Map> contentParts = (List<Map>) content;
            List<MessageRequest.RequestTextBlock> textBlocks = new ArrayList<>();
            for (Map part : contentParts) {
                if("text".equals(part.get("type"))) {
                    MessageRequest.RequestTextBlock textBlock = new MessageRequest.RequestTextBlock();
                    textBlock.setType("text");
                    textBlock.setText((String) part.get("text"));
                    textBlock.setCache_control(convertToCacheControl(part.get("cache_control")));
                    textBlocks.add(textBlock);
                }
            }
            return textBlocks.isEmpty() ? null : textBlocks;
        }
        return null;
    }

    private static MessageRequest.ContentBlock convertContentPartToBlock(Map part) {
        if(part == null)
            return null;

        String type = (String) part.get("type");
        if("text".equals(type)) {
            MessageRequest.TextContentBlock textBlock = new MessageRequest.TextContentBlock();
            textBlock.setText((String) part.get("text"));
            textBlock.setCache_control(convertToCacheControl(part.get("cache_control")));
            return textBlock;
        } else if("image_url".equals(type)) {
            Map imageUrl = (Map) part.get("image_url");
            if(imageUrl != null) {
                String url = (String) imageUrl.get("url");
                if(url != null && url.startsWith("data:")) {
                    String[] dataParts = url.split(",", 2);
                    if(dataParts.length == 2) {
                        String[] headerParts = dataParts[0].split(";");
                        String mediaType = headerParts[0].substring(5); // Remove
                                                                        // "data:"

                        MessageRequest.ImageContentBlock imageBlock = new MessageRequest.ImageContentBlock();
                        MessageRequest.Base64ImageSource imageSource = new MessageRequest.Base64ImageSource();
                        imageSource.setType("base64");
                        imageSource.setMediaType(mediaType);
                        imageSource.setData(dataParts[1]);
                        imageBlock.setSource(imageSource);
                        imageBlock.setCache_control(convertToCacheControl(part.get("cache_control")));
                        return imageBlock;
                    }
                } else if(url != null) {
                    // Handle URL image source
                    MessageRequest.ImageContentBlock imageBlock = new MessageRequest.ImageContentBlock();
                    MessageRequest.URLImageSource imageSource = new MessageRequest.URLImageSource();
                    imageSource.setType("url");
                    imageSource.setUrl(url);
                    imageBlock.setSource(imageSource);
                    imageBlock.setCache_control(convertToCacheControl(part.get("cache_control")));
                    return imageBlock;
                }
            }
        }
        return null;
    }

    private static MessageRequest.ToolChoice convertToolChoice(Object toolChoice) {
        if(toolChoice instanceof String) {
            String choice = (String) toolChoice;
            switch (choice.toLowerCase()) {
            case "none":
                return new MessageRequest.ToolChoiceNone();
            case "auto":
                return new MessageRequest.ToolChoiceAuto();
            case "any":
                return new MessageRequest.ToolChoiceAny();
            default:
                return new MessageRequest.ToolChoiceAuto();
            }
        } else if(toolChoice instanceof Map) {
            Map choiceMap = (Map) toolChoice;
            if("function".equals(choiceMap.get("type"))) {
                Map functionMap = (Map) choiceMap.get("function");
                if(functionMap != null) {
                    String functionName = (String) functionMap.get("name");
                    MessageRequest.ToolChoiceTool toolChoiceTool = new MessageRequest.ToolChoiceTool();
                    toolChoiceTool.setName(functionName);
                    return toolChoiceTool;
                }
            }
        }
        return new MessageRequest.ToolChoiceAuto();
    }

    private static String mapStopReasonToFinish(String stopReason) {
        if(stopReason == null)
            return null;
        switch (stopReason.toLowerCase()) {
        case "end_turn":
            return "stop";
        case "max_tokens":
            return "length";
        case "tool_use":
            return "tool_calls";
        case "refusal":
            return "content_filter";
        default:
            return stopReason;
        }
    }

    @SuppressWarnings("unchecked")
    private static StreamMessageResponse.Delta createDeltaFromMap(Object deltaMap) {
        if(!(deltaMap instanceof Map)) {
            return null;
        }

        Map<String, Object> map = (Map<String, Object>) deltaMap;
        String type = (String) map.get("type");

        switch (type) {
        case "text_delta":
            return new StreamMessageResponse.TextDelta((String) map.get("text"));
        case "input_json_delta":
            return new StreamMessageResponse.InputJsonDelta((String) map.get("partial_json"));
        case "thinking_delta":
            return new StreamMessageResponse.ThinkingDelta((String) map.get("thinking"));
        case "signature_delta":
            return new StreamMessageResponse.SignatureDelta((String) map.get("signature"));
        case "redacted_thinking":
            return new StreamMessageResponse.RedactedThinkingDelta((String) map.get("data"));
        default:
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static StreamMessageResponse.MessageDeltaInfo createMessageDeltaInfoFromMap(Object deltaMap) {
        if(!(deltaMap instanceof Map)) {
            return null;
        }

        Map<String, Object> map = (Map<String, Object>) deltaMap;
        return StreamMessageResponse.MessageDeltaInfo.builder()
                .stopReason((String) map.get("stop_reason"))
                .stopSequence((String) map.get("stop_sequence"))
                .build();
    }
}
