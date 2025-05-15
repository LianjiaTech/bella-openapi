package com.ke.bella.openapi.protocol.completion;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.ImageUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.document.internal.MapDocument;
import software.amazon.awssdk.services.bedrockruntime.model.AnyToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.AutoToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class AwsCompletionConverter {

    /**
     * system message是单独的
     * 只有两个role， user和assistant
     * user message和assistant message必须是交替的
     * tool result只能放在user message里
     * tool use只能放在assistant message里
     * user message有tool result的时候，前面assistant message必须带上对应的tool use
     *
     * @param openAIRequest
     *
     * @return
     */
    public static ConverseRequest convert2AwsRequest(CompletionRequest openAIRequest, AwsProperty property) {
        Pair<List<SystemContentBlock>, List<Message>> pair = generateMsg(openAIRequest.getMessages());
        try {
            rewriteMaxTokens(openAIRequest, property);
            ConverseRequest.Builder builder = ConverseRequest
                    .builder()
                    .modelId(openAIRequest.getModel())
                    .system(pair.getLeft().isEmpty() ? null : pair.getLeft())
                    .messages(pair.getRight())
                    .inferenceConfig(convert2AwsReqConfig(openAIRequest))
                    .toolConfig(CollectionUtils.isEmpty(openAIRequest.getTools())
                            ? null
                            : convert2AwsTool(openAIRequest.getTools(), openAIRequest.getTool_choice()));
            if(MapUtils.isNotEmpty(property.additionalParams)) {
                builder.additionalModelRequestFields(convertObjectToDocument(property.additionalParams));
            }
            return builder.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid arguments");
        }
    }

    public static ConverseStreamRequest convert2AwsStreamRequest(CompletionRequest openAIRequest, AwsProperty property) {
        try {
            rewriteMaxTokens(openAIRequest, property);
            Pair<List<SystemContentBlock>, List<software.amazon.awssdk.services.bedrockruntime.model.Message>> pair = generateMsg(
                    openAIRequest.getMessages());
            ConverseStreamRequest.Builder builder = ConverseStreamRequest
                    .builder()
                    .modelId(openAIRequest.getModel())
                    .system(pair.getLeft().isEmpty() ? null : pair.getLeft())
                    .messages(pair.getRight())
                    .inferenceConfig(convert2AwsReqConfig(openAIRequest))
                    .toolConfig(CollectionUtils.isEmpty(openAIRequest.getTools())
                            ? null
                            : convert2AwsTool(openAIRequest.getTools(), openAIRequest.getTool_choice()));
            if(MapUtils.isNotEmpty(property.additionalParams)) {
                builder.additionalModelRequestFields(convertObjectToDocument(property.additionalParams));
            }
            return builder.build();
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid arguments");
        }
    }

    private static void rewriteMaxTokens(CompletionRequest openAIRequest, AwsProperty property) {
        if(property.supportThink) {
            int maxTokens = property.defaultMaxTokens;
            if(openAIRequest.getMax_tokens() != null) {
                maxTokens = Integer.min(openAIRequest.getMax_tokens() + property.budgetTokens, maxTokens);
            }
            openAIRequest.setMax_tokens(maxTokens);
        }
    }

    public static CompletionResponse convert2OpenAIResponse(ConverseResponse response) {
        if(!response.sdkHttpResponse().isSuccessful()) {
            OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                    .message(response.sdkHttpResponse().statusText().orElse(""))
                    .type(HttpStatus.valueOf(response.sdkHttpResponse().statusCode()).getReasonPhrase())
                    .httpCode(response.sdkHttpResponse().statusCode())
                    .build();
            CompletionResponse completionResponse = new CompletionResponse();
            completionResponse.setError(error);
            return completionResponse;
        }
        software.amazon.awssdk.services.bedrockruntime.model.Message message = response.output().message();
        com.ke.bella.openapi.protocol.completion.Message openAiMsg = new com.ke.bella.openapi.protocol.completion.Message();
        openAiMsg.setRole("assistant");
        openAiMsg.setContent(convert2OpenAIContent(message.content()));
        openAiMsg.setReasoning_content(convert2OpenAIReasoningContent(message.content()));
        openAiMsg.setTool_calls(convert2OpenAIToolCall(message.content()));
        CompletionResponse.Choice choice = new CompletionResponse.Choice();
        choice.setMessage(openAiMsg);
        choice.setIndex(0);
        choice.setFinish_reason(response.stopReasonAsString());
        CompletionResponse.TokenUsage tokenUsage = new CompletionResponse.TokenUsage();
        tokenUsage.setPrompt_tokens(response.usage().inputTokens());
        tokenUsage.setCompletion_tokens(response.usage().outputTokens());
        tokenUsage.setTotal_tokens(response.usage().totalTokens());
        return CompletionResponse.builder()
                .created(DateTimeUtils.getCurrentSeconds())
                .choices(Lists.newArrayList(choice))
                .usage(tokenUsage).build();
    }

    public static StreamCompletionResponse convert2OpenAIStreamResponse(ContentBlockStart response, int index) {
        com.ke.bella.openapi.protocol.completion.Message openAiMsg = new com.ke.bella.openapi.protocol.completion.Message();
        openAiMsg.setRole("assistant");
        if(response.toolUse() != null) {
            com.ke.bella.openapi.protocol.completion.Message.ToolCall toolCall = new com.ke.bella.openapi.protocol.completion.Message.ToolCall();
            ToolUseBlockStart toolUseBlock = response.toolUse();
            toolCall.setType("function");
            toolCall.setIndex(index);
            toolCall.setId(toolUseBlock.toolUseId());
            com.ke.bella.openapi.protocol.completion.Message.FunctionCall fc = new com.ke.bella.openapi.protocol.completion.Message.FunctionCall();
            fc.setName(toolUseBlock.name());
            toolCall.setFunction(fc);
            openAiMsg.setTool_calls(Lists.newArrayList(toolCall));
        }
        StreamCompletionResponse.Choice choice = new StreamCompletionResponse.Choice();
        choice.setDelta(openAiMsg);
        choice.setIndex(index);
        return StreamCompletionResponse.builder()
                .choices(Lists.newArrayList(choice))
                .created(DateTimeUtils.getCurrentSeconds())
                .build();
    }

    public static StreamCompletionResponse convert2OpenAIStreamResponse(ContentBlockDelta response, int index) {
        com.ke.bella.openapi.protocol.completion.Message openAiMsg = new com.ke.bella.openapi.protocol.completion.Message();
        openAiMsg.setRole("assistant");
        openAiMsg.setContent(response.text());
        if(response.toolUse() != null) {
            com.ke.bella.openapi.protocol.completion.Message.ToolCall toolCall = new com.ke.bella.openapi.protocol.completion.Message.ToolCall();
            ToolUseBlockDelta toolUseBlock = response.toolUse();
            toolCall.setType("function");
            toolCall.setIndex(index);
            com.ke.bella.openapi.protocol.completion.Message.FunctionCall fc = new com.ke.bella.openapi.protocol.completion.Message.FunctionCall();
            fc.setArguments(toolUseBlock.input());
            toolCall.setFunction(fc);
            openAiMsg.setTool_calls(Lists.newArrayList(toolCall));
        } else if(response.reasoningContent() != null) {
            openAiMsg.setReasoning_content(response.reasoningContent().text());
        }
        StreamCompletionResponse.Choice choice = new StreamCompletionResponse.Choice();
        choice.setDelta(openAiMsg);
        choice.setIndex(index);
        return StreamCompletionResponse.builder().choices(Lists.newArrayList(choice))
                .created(DateTimeUtils.getCurrentSeconds())
                .build();
    }

    public static StreamCompletionResponse convertTo2OpenAIStreamResponse(TokenUsage usage) {
        CompletionResponse.TokenUsage tokenUsage = new CompletionResponse.TokenUsage();
        tokenUsage.setPrompt_tokens(usage.inputTokens());
        tokenUsage.setCompletion_tokens(usage.outputTokens());
        tokenUsage.setTotal_tokens(usage.totalTokens());
		tokenUsage.setCache_read_input_tokens(usage.cacheReadInputTokens());
		tokenUsage.setCache_write_input_tokens(usage.cacheWriteInputTokens());
        return StreamCompletionResponse.builder()
                .created(DateTimeUtils.getCurrentSeconds())
                .usage(tokenUsage).build();
    }

    private static Object convert2OpenAIContent(List<ContentBlock> contentBlocks) {
        return contentBlocks.stream().map(ContentBlock::text).filter(Objects::nonNull).findAny().orElse("");
    }

    private static String convert2OpenAIReasoningContent(List<ContentBlock> contentBlocks) {
        return contentBlocks.stream().map(ContentBlock::reasoningContent).filter(Objects::nonNull)
                .map(ReasoningContentBlock::reasoningText)
                .filter(Objects::nonNull)
                .map(ReasoningTextBlock::text)
                .filter(Objects::nonNull)
                .findAny().orElse(null);
    }

    private static List<com.ke.bella.openapi.protocol.completion.Message.ToolCall> convert2OpenAIToolCall(List<ContentBlock> contentBlocks) {
        List<com.ke.bella.openapi.protocol.completion.Message.ToolCall> toolCalls = new ArrayList<>();
        int index = 0;
        for (ContentBlock contentBlock : contentBlocks) {
            if(contentBlock.toolUse() == null) {
                continue;
            }
            toolCalls.add(convert2OpenAIToolCall(contentBlock.toolUse(), index++));
        }
        return toolCalls.isEmpty() ? null : toolCalls;
    }

    private static com.ke.bella.openapi.protocol.completion.Message.ToolCall convert2OpenAIToolCall(ToolUseBlock toolUseBlock, int index) {
        com.ke.bella.openapi.protocol.completion.Message.ToolCall toolCall = new com.ke.bella.openapi.protocol.completion.Message.ToolCall();
        toolCall.setId(toolUseBlock.toolUseId());
        toolCall.setType("function");
        toolCall.setIndex(index);
        com.ke.bella.openapi.protocol.completion.Message.FunctionCall fc = new com.ke.bella.openapi.protocol.completion.Message.FunctionCall();
        fc.setName(toolUseBlock.name());
        fc.setArguments(convertDocumentToOpenAIArguments(toolUseBlock.input()));
        toolCall.setFunction(fc);
        return toolCall;
    }

    private static Pair<List<SystemContentBlock>, List<Message>> generateMsg(List<com.ke.bella.openapi.protocol.completion.Message> openAIMsgList) {
        List<SystemContentBlock> systemContentBlocks = new ArrayList<>();
        List<software.amazon.awssdk.services.bedrockruntime.model.Message> messages = new ArrayList<>();
        String currentRole = "";
        List<ContentBlock> currentContents = new ArrayList<>();
        for (com.ke.bella.openapi.protocol.completion.Message message : openAIMsgList) {
            String role = message.getRole().equals("tool") ? "user" : message.getRole();
            if(role.equals("system") || role.equals("developer")) {
                if(message.getContent() != null && !"".equals(message.getContent())) {
                    systemContentBlocks.add(convert2AwsSystemContent(message));
                }
            } else {
                if(!role.equals(currentRole)) {
                    if(CollectionUtils.isNotEmpty(currentContents)) {
                        messages.add(software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                                .role(currentRole.equals("user") ? ConversationRole.USER : ConversationRole.ASSISTANT)
                                .content(currentContents)
                                .build());
                        currentContents = new ArrayList<>();
                    }
                    currentRole = role;
                }
                List<ContentBlock> contents = convert2AwsContent(message);
                currentContents.addAll(contents);
            }
        }
        if(CollectionUtils.isNotEmpty(currentContents)) {
            messages.add(Message.builder()
                    .role(currentRole.equals("user") ? ConversationRole.USER : ConversationRole.ASSISTANT)
                    .content(currentContents)
                    .build());
        }
        return Pair.of(systemContentBlocks, messages);
    }

    private static SystemContentBlock convert2AwsSystemContent(com.ke.bella.openapi.protocol.completion.Message openAiMsg) {
        SystemContentBlock.Builder builder = SystemContentBlock.builder();

        Object content = openAiMsg.getContent();
        if (content instanceof List) {
            List<?> contentList = (List<?>) content;
            if (!contentList.isEmpty() && contentList.get(0) instanceof Map) {
                Map<?, ?> contentMap = (Map<?, ?>) contentList.get(0);
                if (contentMap.containsKey("text")) {
                    builder.text(contentMap.get("text").toString());

                    // Add cache point if cache_control is present, always use "default" type
                    if (contentMap.containsKey("cache_control")) {
                        builder.cachePoint(CachePointBlock.builder()
                                .type("default")
                                .build());
                    }
                }
            }
        } else if (content != null) {
            builder.text(content.toString());
        }

        return builder.build();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<ContentBlock> convert2AwsContent(com.ke.bella.openapi.protocol.completion.Message message) {
        List<ContentBlock> contentBlocks = new ArrayList<>();
        if(message.getRole().equals("tool")) {
            contentBlocks.add(ContentBlock.fromToolResult(
                    ToolResultBlock.builder()
                            .toolUseId(message.getTool_call_id())
                            .content(ToolResultContentBlock.fromText(message.getContent().toString()))
                            .build()));
        } else {
            if(message.getContent() != null) {
                if(message.getContent() instanceof String) {
                    contentBlocks.add(convert2TextBlock((String) message.getContent()));
                } else if(message.getContent() instanceof List) {
                    List<Object> contentList = (List<Object>) message.getContent();
                    for (Object content : contentList) {
                        Map contentMap = (Map) content;
                        String type = contentMap.get("type").toString();
                        if(type.equals("text")) {
                            contentBlocks.add(convert2TextBlock(contentMap));
                        } else if(type.equals("image_url")) {
                            String url = ((Map) contentMap.get("image_url")).get("url").toString();
                            Map<String, Object> cacheControl = (Map<String, Object>) contentMap.get("cache_control");
                            contentBlocks.add(convert2ImageBlock(url, cacheControl));
                        } else if(type.equals("function")) {
                            List toolCalls = (List) contentMap.get("tool_calls");
                            for (Object toolCall : toolCalls) {
                                Map toolCallMap = (Map) toolCall;
                                String id = toolCallMap.get("id").toString();
                                Map<String, Object> functionMap = (Map<String, Object>) toolCallMap.get("function");
                                String name = functionMap.get("name").toString();
                                String arguments = functionMap.get("arguments").toString();
                                contentBlocks.add(convert2ToolUseBlock(id, name, arguments));
                            }
                        }
                    }
                }
            }

            if(message.getTool_calls() != null && !message.getTool_calls().isEmpty()) {
                for (com.ke.bella.openapi.protocol.completion.Message.ToolCall toolCall : message.getTool_calls()) {
                    contentBlocks.add(convert2ToolUseBlock(
                            toolCall.getId(),
                            toolCall.getFunction().getName(),
                            toolCall.getFunction().getArguments()));
                }
            }
        }
        return contentBlocks;
    }

    private static ContentBlock convert2TextBlock(String text) {
        return ContentBlock.fromText(text);
    }

	@SuppressWarnings({ "unchecked", "rawtypes" })
    private static ContentBlock convert2ToolUseBlock(String toolUseId, String name, String arguments) {
        return ContentBlock.fromToolUse(ToolUseBlock
                .builder()
                .toolUseId(toolUseId)
                .name(name)
                .input(convertMapToDocument(JacksonUtils.deserialize(arguments, Map.class)))
                .build());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static ContentBlock convert2TextBlock(Map<String, Object> contentMap) {
        ContentBlock.Builder blockBuilder = ContentBlock.builder().text(contentMap.get("text").toString());

        // Handle cache_control if present, always use "default" type
        if (contentMap.containsKey("cache_control")) {
            blockBuilder.cachePoint(CachePointBlock.builder()
                    .type("default")
                    .build());
        }

        return blockBuilder.build();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static ContentBlock convert2ImageBlock(String image, Map<String, Object> cacheControl) {
        if(!ImageUtils.isDateBase64(image)) {
            throw new IllegalArgumentException("aws的图片仅支持data base64String");
        }
        String format = ImageUtils.extractImageFormat(image);
        String base64String = ImageUtils.extractBase64ImageData(image);
        byte[] decodedBytes = Base64.getDecoder().decode(base64String);

        ContentBlock.Builder blockBuilder = ContentBlock.builder()
                .image(ImageBlock.builder()
                        .format(format)
                        .source(ImageSource.builder()
                                .bytes(SdkBytes.fromByteArray(decodedBytes))
                                .build())
                        .build());

        // Add cache point if cache_control is present
        if (cacheControl != null) {
            blockBuilder.cachePoint(CachePointBlock.builder()
                    .type("default")
                    .build());
        }

        return blockBuilder.build();
    }

    private static ToolConfiguration convert2AwsTool(List<com.ke.bella.openapi.protocol.completion.Message.Tool> tools, Object toolChoice) {
        List<software.amazon.awssdk.services.bedrockruntime.model.Tool> list = new ArrayList<>();
        for (com.ke.bella.openapi.protocol.completion.Message.Tool tool : tools) {
            list.add(convert2AwsTool(tool));
        }

        ToolConfiguration.Builder builder = ToolConfiguration.builder().tools(list);

        if(toolChoice == null) {
            // 默认为auto
            builder.toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build()));
        } else if(toolChoice instanceof String) {
            String choice = (String) toolChoice;
            switch (choice.toLowerCase()) {
            case "none":
                // AWS没有直接对应的"none"选项
                builder.tools(Collections.emptyList());
                builder.toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build()));
                break;
            case "auto":
                builder.toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build()));
                break;
            case "required":
                builder.toolChoice(ToolChoice.fromAny(AnyToolChoice.builder().build()));
                break;
            default:
                builder.toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build())); // 默认为auto
                break;
            }
        } else if(toolChoice instanceof Map) {
            try {
                Map<String, Object> choiceMap = (Map<String, Object>) toolChoice;
                String type = (String) choiceMap.get("type");

                if("function".equals(type)) {
                    // 指定特定函数
                    String functionName = (String) choiceMap.get("name");
                    if(functionName != null && !functionName.isEmpty()) {
                        // 使用fromTool方法并设置工具名称
                        builder.toolChoice(ToolChoice.fromTool(SpecificToolChoice.builder().name(functionName).build()));
                    } else {
                        builder.toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build())); // 如果未指定函数名，默认为auto
                    }
                } else {
                    // 其他内置工具类型，默认为auto
                    builder.toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build()));
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse toolChoice object: {}", e.getMessage());
                builder.toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build())); // 解析失败，默认为auto
            }
        } else {
            // 其他类型，默认为auto
            builder.toolChoice(ToolChoice.fromAuto(AutoToolChoice.builder().build()));
        }

        return builder.build();
    }

    @SuppressWarnings({ "rawtypes" })
    private static software.amazon.awssdk.services.bedrockruntime.model.Tool convert2AwsTool(
            com.ke.bella.openapi.protocol.completion.Message.Tool tool) {
        Map schemaMap = JacksonUtils.toMap(tool.getFunction().getParameters());
        software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification.Builder toolBuilder = software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification
                .builder();
        toolBuilder.name(tool.getFunction().getName());
        toolBuilder.description(tool.getFunction().getDescription());
        toolBuilder.inputSchema(ToolInputSchema.builder().json(convertObjectToDocument(schemaMap)).build());
        return software.amazon.awssdk.services.bedrockruntime.model.Tool.fromToolSpec(toolBuilder.build());
    }

    private static InferenceConfiguration convert2AwsReqConfig(CompletionRequest openAIRequest) {
        InferenceConfiguration.Builder builder = InferenceConfiguration
                .builder()
                .maxTokens(openAIRequest.getMax_tokens())
                .stopSequences()
                .topP(openAIRequest.getTop_p())
                .temperature(openAIRequest.getTemperature());
        if(openAIRequest.getStop() != null) {
            if(openAIRequest.getStop() instanceof String) {
                String[] stops = new String[] { (String) openAIRequest.getStop() };
                builder.stopSequences(stops);
            } else {
                builder.stopSequences((String[]) openAIRequest.getStop());
            }
        }
        return builder.build();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Document convertObjectToDocument(Object value) {
        if(value == null) {
            return Document.fromNull();
        } else if(value instanceof String) {
            return Document.fromString((String) value);
        } else if(value instanceof Boolean) {
            return Document.fromBoolean((Boolean) value);
        } else if(value instanceof Integer) {
            return Document.fromNumber((Integer) value);
        } else if(value instanceof Long) {
            return Document.fromNumber((Long) value);
        } else if(value instanceof Float) {
            return Document.fromNumber((Float) value);
        } else if(value instanceof Double) {
            return Document.fromNumber((Double) value);
        } else if(value instanceof BigDecimal) {
            return Document.fromNumber((BigDecimal) value);
        } else if(value instanceof BigInteger) {
            return Document.fromNumber((BigInteger) value);
        } else if(value instanceof List) {
            List listValue = (List) value;
            return Document.fromList((List<Document>) listValue.stream().map(AwsCompletionConverter::convertObjectToDocument)
                    .collect(Collectors.toList()));
        } else if(value instanceof Map) {
            return convertMapToDocument((Map<String, Object>) value);
        } else {
            throw new IllegalArgumentException("Unsupported value type:" + value.getClass().getSimpleName());
        }
    }

    private static Document convertMapToDocument(Map<String, Object> value) {
        Map<String, Document> attr = value.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> convertObjectToDocument(e.getValue())));
        return Document.fromMap(attr);
    }

    private static Object convertDocumentToObject(Document document) {
        if(document.isNull()) {
            return null;
        } else if(document.isString()) {
            return document.asString();
        } else if(document.isBoolean()) {
            return document.asBoolean();
        } else if(document.isNumber()) {
            return document.asNumber().doubleValue();
        } else if(document.isList()) {
            List<Document> documents = document.asList();
            return documents.stream().map(AwsCompletionConverter::convertDocumentToObject).collect(Collectors.toList());
        } else if(document.isMap()) {
            return convertDocumentToMap((MapDocument) document);
        }
        return null;
    }

    private static Map<String, Object> convertDocumentToMap(MapDocument document) {
        Map<String, Document> documentMap = document.asMap();
        Map<String, Object> map = new HashMap<>();
        documentMap.entrySet().forEach(x -> map.put(x.getKey(), convertDocumentToObject(x.getValue())));
        return map;
    }

    private static String convertDocumentToOpenAIArguments(Document document) {
        Object object = convertDocumentToObject(document);
        if(object == null) {
            return null;
        }
        if(document.isMap() || document.isList()) {
            return JacksonUtils.serialize(object);
        } else {
            return object.toString();
        }
    }
}
