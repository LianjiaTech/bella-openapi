package com.ke.bella.openapi.protocol.completion;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.protocol.completion.gemini.Candidate;
import com.ke.bella.openapi.protocol.completion.gemini.Content;
import com.ke.bella.openapi.protocol.completion.gemini.FunctionCall;
import com.ke.bella.openapi.protocol.completion.gemini.FunctionResponse;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiRequest;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiResponse;
import com.ke.bella.openapi.protocol.completion.gemini.GenerationConfig;
import com.ke.bella.openapi.protocol.completion.gemini.LogprobsResult;
import com.ke.bella.openapi.protocol.completion.gemini.Part;
import com.ke.bella.openapi.protocol.completion.gemini.SystemInstruction;
import com.ke.bella.openapi.protocol.completion.gemini.Tool;
import com.ke.bella.openapi.protocol.completion.gemini.UsageMetadata;
import com.ke.bella.openapi.protocol.completion.gemini.UsageMetadata.Modality;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.ImageUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class VertexConverter {

    public static GeminiRequest convertToVertexRequest(CompletionRequest openaiRequest, VertexProperty property) {
        GeminiRequest.GeminiRequestBuilder builder = GeminiRequest.builder();
        
        // Convert messages to contents
        List<Content> contents = convertMessages(openaiRequest.getMessages());

        
        // Convert system message to systemInstruction
        SystemInstruction systemInstruction = extractSystemInstruction(openaiRequest.getMessages());
        if (systemInstruction != null) {
            if(property.isSupportSystemInstruction()) {
                builder.systemInstruction(systemInstruction);
            } else {
                contents.add(0, Content.builder()
                        .role("user")
                        .parts(systemInstruction.getParts())
                        .build());
            }
        }

        builder.contents(contents);

        // Convert tools
        if (CollectionUtils.isNotEmpty(openaiRequest.getTools())) {
            List<Tool> tools = convertTools(openaiRequest.getTools());
            builder.tools(tools);
        }
        
        // Convert generation config
        GenerationConfig generationConfig = convertGenerationConfig(openaiRequest, property);
        builder.generationConfig(generationConfig);
        
        return builder.build().offSafetySettings();
    }
    
    public static CompletionResponse convertToOpenAIResponse(GeminiResponse vertexResponse) {
        if (vertexResponse == null || CollectionUtils.isEmpty(vertexResponse.getCandidates())) {
            return CompletionResponse.builder().build();
        }
        
        List<CompletionResponse.Choice> choices = new ArrayList<>();
        for (int i = 0; i < vertexResponse.getCandidates().size(); i++) {
            Candidate candidate = vertexResponse.getCandidates().get(i);
            CompletionResponse.Choice choice = convertCandidate(candidate, i);
            choices.add(choice);
        }
        
        // Convert usage
        CompletionResponse.TokenUsage usage = convertUsage(vertexResponse.getUsageMetadata());
        
        return CompletionResponse.builder()
                .id(vertexResponse.getResponseId())
                .object("chat.completion")
                .created(DateTimeUtils.getCurrentSeconds())
                .model(vertexResponse.getModelVersion())
                .choices(choices)
                .usage(usage)
                .build();
    }

    @SuppressWarnings("all")
    public static StreamCompletionResponse convertGeminiToStreamResponse(GeminiResponse geminiResponse, Long created) {
        StreamCompletionResponse.StreamCompletionResponseBuilder builder = StreamCompletionResponse.builder()
                .id(geminiResponse.getResponseId())
                .object("chat.completion.chunk")
                .created(created)
                .model(geminiResponse.getModelVersion());

        boolean isFinal = false;
        // 处理第一个候选项（通常 Gemini 只返回一个候选）
        if (CollectionUtils.isNotEmpty(geminiResponse.getCandidates())) {
            isFinal = geminiResponse.getCandidates().get(0).getFinishReason() != null;

            StreamCompletionResponse.Choice choice = VertexConverter.convertStreamCandidate(
                    geminiResponse.getCandidates().get(0), 0, isFinal);

            builder.choices(Collections.singletonList(choice));
        }

        // 处理 usage metadata
        if (geminiResponse.getUsageMetadata() != null && isFinal) {
            builder.usage(VertexConverter.convertUsage(geminiResponse.getUsageMetadata()));
        }

        return builder.build();
    }
    
    private static List<Content> convertMessages(List<Message> messages) {
        Map<String, String> toolCallCache = new HashMap<>();
        List<String> toolCallSorts = new ArrayList<>();
        List<Content> contents = new ArrayList<>();
        Map<String, Part> toolParts = new HashMap<>();
        for(Message msg : messages) {
            if("system".equals(msg.getRole()) || "developer".equals(msg.getRole())) {
                continue;
            }
            if("tool".equals(msg.getRole())) {
                if(StringUtils.hasText(msg.getTool_call_id())) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("result", msg.getContent() != null ? msg.getContent().toString() : "");
                    FunctionResponse functionResponse = FunctionResponse.builder()
                            .name(toolCallCache.get(msg.getTool_call_id()))
                            .response(response)
                            .build();
                    toolParts.put(msg.getTool_call_id(), Part.builder().functionResponse(functionResponse).build());
                }
                continue;
            }
            if("assistant".equals(msg.getRole())) {
                addToolParts(contents, toolParts, toolCallSorts);
                toolCallCache.clear();
            }
            contents.add(convertMessage(msg, toolCallCache, toolCallSorts));
        }
        addToolParts(contents, toolParts, toolCallSorts);
        return contents;
    }

    private static void addToolParts(List<Content> contents, Map<String, Part> toolParts, List<String> toolCallSorts) {
        if(!toolParts.isEmpty()) {
            List<Part> parts = toolCallSorts.stream().map(toolParts::get).filter(Objects::nonNull).collect(Collectors.toList());
            Content last = contents.get(contents.size() - 1);
            if("model".equals(last.getRole())) {
                contents.add(Content.builder()
                        .role("user")
                        .parts(parts)
                        .build());
            } else {
                last.getParts().addAll(parts);
            }
        }
        toolParts.clear();
        toolCallSorts.clear();
    }
    
    /**
     * 辅助方法：为 Part.PartBuilder 设置 thoughtSignature（如果存在）
     */
    private static Part.PartBuilder applyThoughtSignature(Part.PartBuilder builder, String thoughtSignature) {
        if (StringUtils.hasText(thoughtSignature)) {
            builder.thoughtSignature(thoughtSignature);
        }
        return builder;
    }

    private static Content convertMessage(Message message, Map<String, String> toolCallCache, List<String> toolCallSorts) {
        List<Part> parts = new ArrayList<>();
        
        // 使用 Message.thoughtSignature 字段
        String thoughtSignature = message.getReasoning_content_signature();
        addContentToParts(parts, message.getContent(), thoughtSignature);
        
        // Handle tool calls
        if (CollectionUtils.isNotEmpty(message.getTool_calls())) {
            for (Message.ToolCall toolCall : message.getTool_calls()) {
                FunctionCall functionCall = FunctionCall.builder()
                        .name(toolCall.getFunction().getName())
                        .args(parseArguments(toolCall.getFunction().getArguments()))
                        .build();
                // 创建 function call part 并附加 thoughtSignature
                Part part = applyThoughtSignature(
                    Part.builder().functionCall(functionCall),
                    thoughtSignature
                ).build();

                parts.add(part);
                toolCallCache.put(toolCall.getId(), toolCall.getFunction().getName());
                toolCallSorts.add(toolCall.getId());
            }
        }
        
        String role = "assistant".equals(message.getRole()) ? "model" : "user";
        
        return Content.builder()
                .role(role)
                .parts(parts)
                .build();
    }

    private static void addContentToParts(List<Part> parts, Object content, String thoughtSignature) {
        // Handle content (can be String, Map, or List for multimodal)
        if (content instanceof String) {
            String textContent = (String) content;
            if (StringUtils.hasText(textContent)) {
                Part part = applyThoughtSignature(
                    Part.builder().text(textContent),
                    thoughtSignature
                ).build();
                parts.add(part);
            }
        } else if (content instanceof Map) {
            // Handle content as a map with text field
            @SuppressWarnings("unchecked")
            Map<String, Object> contentMap = (Map<String, Object>) content;
            String text = (String) contentMap.get("text");
            if (StringUtils.hasText(text)) {
                Part part = applyThoughtSignature(
                    Part.builder().text(text),
                    thoughtSignature
                ).build();
                parts.add(part);
            }
        } else if (content instanceof List) {
            // Handle multimodal content
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) content;
            for (Map<String, Object> contentItem : contentList) {
                Part part = convertContentItem(contentItem, thoughtSignature);
                if (part != null) {
                    parts.add(part);
                }
            }
        }
    }
    
    private static Part convertContentItem(Map<String, Object> contentItem, String thoughtSignature) {
        String type = (String) contentItem.get("type");
        if (type == null) return null;
        
        switch (type) {
            case "text":
                String text = (String) contentItem.get("text");
                return applyThoughtSignature(
                    Part.builder().text(text),
                    thoughtSignature
                ).build();
            case "image_url":
                @SuppressWarnings("unchecked")
                Map<String, Object> imageUrl = (Map<String, Object>) contentItem.get("image_url");
                if (imageUrl != null && imageUrl.get("url") != null) {
                    String url = (String) imageUrl.get("url");
                    if(!ImageUtils.isDateBase64(url)) {
                        throw new IllegalArgumentException("gemini的图片仅支持data base64String");
                    }
                    // Base64 inline data
                    String[] parts = url.split(",", 2);
                    if (parts.length == 2) {
                        String mimeType = parts[0].split(":")[1].split(";")[0];
                        return applyThoughtSignature(
                            Part.builder().inlineData(
                                Part.InlineData.builder()
                                    .mimeType(mimeType)
                                    .data(parts[1])
                                    .build()
                            ),
                            thoughtSignature
                        ).build();
                    }
                }
                break;
            default:
                log.warn("Unsupported content type: {}", type);
                break;
        }
        return null;
    }
    
    private static SystemInstruction extractSystemInstruction(List<Message> messages) {
        return messages.stream()
                .filter(msg -> "system".equals(msg.getRole()) || "developer".equals(msg.getRole()))
                .findFirst()
                .map(msg -> {
                    List<Part> parts = new ArrayList<>();
                    addContentToParts(parts, msg.getContent(), null);
                    return SystemInstruction.builder()
                            .role("system")
                            .parts(parts)
                            .build();
                })
                .orElse(null);
    }
    
    private static List<Tool> convertTools(List<Message.Tool> openaiTools) {
        List<Tool.FunctionDeclaration> functionDeclarations = openaiTools.stream()
                .map(tool -> {
                    Message.Function function = tool.getFunction();
                    return Tool.FunctionDeclaration.builder()
                            .name(function.getName())
                            .description(function.getDescription())
                            .parameters(function.getParameters())
                            .build();
                })
                .collect(Collectors.toList());
        
        return Collections.singletonList(
                Tool.builder().functionDeclarations(functionDeclarations).build());
    }
    
    private static GenerationConfig convertGenerationConfig(CompletionRequest request, VertexProperty property) {
        GenerationConfig.GenerationConfigBuilder builder = GenerationConfig.builder();

        if(request.getN() != null) {
            builder.candidateCount(request.getN());
        }
        
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getTop_p() != null) {
            builder.topP(request.getTop_p());
        }
        if (request.getMax_tokens() != null) {
            builder.maxOutputTokens(request.getMax_tokens());
        }
        if (request.getStop() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> stopList = (List<String>) request.getStop();
            if (CollectionUtils.isNotEmpty(stopList)) {
                builder.stopSequences(stopList);
            }
        } else if(request.getStop() instanceof String){
            builder.stopSequences(Lists.newArrayList(request.getStop().toString()));
        }
        if (request.getPresence_penalty() != null) {
            builder.presencePenalty(request.getPresence_penalty());
        }
        if (request.getFrequency_penalty() != null) {
            builder.frequencyPenalty(request.getFrequency_penalty());
        }
        if (request.getSeed() != null) {
            builder.seed(request.getSeed());
        }
        //todo: 开启Logprobs存在问题，暂不支持
//        if (request.getLogprobs() != null) {
//            builder.responseLogprobs(request.getLogprobs());
//        }
//        if (request.getTop_logprobs() != null) {
//            builder.logprobs(request.getTop_logprobs());
//        }
        if(request.getReasoning_effort() != null && property.isSupportThinkConfig()) {
            builder.thinkingConfig(new GenerationConfig.ThinkingConfig());
        }
        return builder.build();
    }

    private static CompletionResponse.Choice convertCandidate(Candidate candidate, int index) {
        Message message = convertContentToMessage(candidate.getContent());
        String finishReason = convertFinishReason(candidate.getFinishReason());

        return CompletionResponse.Choice.builder()
                .index(index)
                .message(message)
                .finish_reason(finishReason)
                .logprobs(convertLogprobs(candidate.getLogprobsResult()))
                .build();
    }

    private static StreamCompletionResponse.Choice convertStreamCandidate(Candidate candidate, int index, boolean isFinal) {
        Message delta = convertContentToMessage(candidate.getContent());
        String finishReason = isFinal ? convertFinishReason(candidate.getFinishReason()) : null;

        return StreamCompletionResponse.Choice.builder()
                .index(index)
                .delta(delta)
                .finish_reason(finishReason)
                .logprobs(convertLogprobs(candidate.getLogprobsResult()))
                .build();
    }

    private static CompletionResponse.Logprobs convertLogprobs(LogprobsResult logprobsResult) {
        if (logprobsResult == null || CollectionUtils.isEmpty(logprobsResult.getChosenCandidates())) {
            return null;
        }

        List<CompletionResponse.Logprobs.Content> contents = new ArrayList<>();
        List<LogprobsResult.ChosenCandidate> chosenCandidates = logprobsResult.getChosenCandidates();
        List<LogprobsResult.TopCandidates> topCandidates = logprobsResult.getTopCandidates();

        for (int i = 0; i < chosenCandidates.size(); i++) {
            LogprobsResult.ChosenCandidate chosenCandidate = chosenCandidates.get(i);
            CompletionResponse.Logprobs.Content.ContentBuilder contentBuilder = CompletionResponse.Logprobs.Content
                    .builder()
                    .token(chosenCandidate.getToken())
                    .logprob(chosenCandidate.getLogProbability());

            if (CollectionUtils.isNotEmpty(topCandidates) && topCandidates.size() > i
                    && CollectionUtils.isNotEmpty(topCandidates.get(i).getCandidates())) {
                List<CompletionResponse.Logprobs.TopLogprob> topLogprobs = topCandidates.get(i).getCandidates().stream()
                        .map(candidate -> CompletionResponse.Logprobs.TopLogprob.builder()
                                .token(candidate.getToken())
                                .logprob(candidate.getLogProbability())
                                .build())
                        .collect(Collectors.toList());
                contentBuilder.top_logprobs(topLogprobs);
            }

            contents.add(contentBuilder.build());
        }

        if (contents.isEmpty()) {
            return null;
        }

        return CompletionResponse.Logprobs.builder().content(contents).build();
    }
    
    private static Message convertContentToMessage(Content content) {
        if (content == null || CollectionUtils.isEmpty(content.getParts())) {
            return Message.builder().role("assistant").content("").build();
        }
        
        Message.MessageBuilder builder = Message.builder().role("assistant");
        StringBuilder textContent = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<Message.ToolCall> toolCalls = new ArrayList<>();

        // 按优先级提取 thoughtSignature
        String functionCallThoughtSignature = null;  // 最高优先级
        String inlineDataThoughtSignature = null;    // 次优先级
        String firstThoughtSignature = null;         // 兜底：第一个非空的

        int index = 0;
        for (Part part : content.getParts()) {
            // 收集不同类型的 thoughtSignature
            if (StringUtils.hasText(part.getThoughtSignature())) {
                if (firstThoughtSignature == null) {
                    firstThoughtSignature = part.getThoughtSignature();
                }
                if (part.getFunctionCall() != null && functionCallThoughtSignature == null) {
                    functionCallThoughtSignature = part.getThoughtSignature();
                }
                if (part.getInlineData() != null && inlineDataThoughtSignature == null) {
                    inlineDataThoughtSignature = part.getThoughtSignature();
                }
            }

            if (StringUtils.hasText(part.getText())) {
                if(Boolean.TRUE == part.getThought()) {
                    reasoning.append(part.getText());
                } else {
                    textContent.append(part.getText());
                }
            }
            
            if (part.getFunctionCall() != null) {
                FunctionCall functionCall = part.getFunctionCall();
                Message.ToolCall toolCall = Message.ToolCall.builder()
                        .id("call_" + UUID.randomUUID().toString().replace("-", ""))
                        .type("function")
                        .index(index++)
                        .function(Message.FunctionCall.builder()
                                .name(functionCall.getName())
                                .arguments(serializeArguments(functionCall.getArgs()))
                                .build())
                        .build();
                toolCalls.add(toolCall);
            }

            if (part.getInlineData() != null) {
                textContent.append("\n");
                textContent.append("<inline>");
                textContent.append("<data>");
                textContent.append(part.getInlineData().getData());
                textContent.append("</data>");
                textContent.append("<mimeType>");
                textContent.append(part.getInlineData().getMimeType());
                textContent.append("</mimeType>");
                textContent.append("</inline>");
                textContent.append("\n");
            }
        }

        if(textContent.length() > 0) {
            builder.content(textContent.toString());
        }

        if(reasoning.length() > 0) {
            builder.reasoning_content(reasoning.toString());
        }

        if (!toolCalls.isEmpty()) {
            builder.tool_calls(toolCalls);
        }

        // 按优先级设置 thoughtSignature：functionCall > inlineData > 第一个非空
        String thoughtSignature = functionCallThoughtSignature != null ? functionCallThoughtSignature
                : (inlineDataThoughtSignature != null ? inlineDataThoughtSignature : firstThoughtSignature);

        if (StringUtils.hasText(thoughtSignature)) {
            builder.reasoning_content_signature(thoughtSignature);
        }
        
        return builder.build();
    }
    
    public static CompletionResponse.TokenUsage convertUsage(UsageMetadata usageMetadata) {
        if (usageMetadata == null) {
            return null;
        }
        
        CompletionResponse.TokenUsage.TokenUsageBuilder builder = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(usageMetadata.getPromptTokenCount() != null ? usageMetadata.getPromptTokenCount() : 0)
                .completion_tokens(calculateCompletionTokens(usageMetadata))
                .total_tokens(usageMetadata.getTotalTokenCount() != null ? usageMetadata.getTotalTokenCount() : 0);
        
        CompletionResponse.TokensDetail promptTokensDetail = null;
        
        if(usageMetadata.getCachedContentTokenCount() != null && usageMetadata.getCachedContentTokenCount() > 0) {
            int cachedCount = usageMetadata.getCachedContentTokenCount();
            builder.cache_read_tokens(cachedCount);
            promptTokensDetail = new CompletionResponse.TokensDetail();
            promptTokensDetail.setCached_tokens(cachedCount);
        }
        
        if(usageMetadata.getPromptTokensDetails() != null) {
            if(promptTokensDetail == null) {
                promptTokensDetail = new CompletionResponse.TokensDetail();
            }
            CompletionResponse.TokensDetail finalDetail = promptTokensDetail;
            usageMetadata.getPromptTokensDetails().forEach(detail -> {
                if(Modality.IMAGE.name().equals(detail.getModality())) {
                    finalDetail.setImage_tokens(finalDetail.getImage_tokens() + detail.getTokenCount());
                } else if(Modality.AUDIO.name().equals(detail.getModality())) {
                    finalDetail.setAudio_tokens(finalDetail.getAudio_tokens() + detail.getTokenCount());
                }
            });
        }
        
        if(promptTokensDetail != null) {
            builder.prompt_tokens_details(promptTokensDetail);
        }

        if(usageMetadata.getCandidatesTokensDetails() != null) {
            CompletionResponse.TokensDetail tokensDetail = new CompletionResponse.TokensDetail();
            usageMetadata.getCandidatesTokensDetails().forEach(detail -> {
                if(Modality.IMAGE.name().equals(detail.getModality())) {
                    tokensDetail.setImage_tokens(tokensDetail.getImage_tokens() + detail.getTokenCount());
                } else if(Modality.AUDIO.name().equals(detail.getModality())) {
                    tokensDetail.setAudio_tokens(tokensDetail.getAudio_tokens() + detail.getTokenCount());
                }
            });
            if(usageMetadata.getThoughtsTokenCount() != null && usageMetadata.getThoughtsTokenCount() > 0) {
                tokensDetail.setReasoning_tokens(usageMetadata.getThoughtsTokenCount());
            }
            if(tokensDetail.getImage_tokens() > 0 || tokensDetail.getAudio_tokens() > 0 || tokensDetail.getReasoning_tokens() > 0) {
                builder.completion_tokens_details(tokensDetail);
            }
        }
        
        if(usageMetadata.getCacheTokensDetails() != null) {
            usageMetadata.getCacheTokensDetails().forEach(detail -> {
                if(Modality.IMAGE.name().equals(detail.getModality()) || Modality.AUDIO.name().equals(detail.getModality())) {
                    CompletionResponse.TokensDetail promptDetail = builder.build().getPrompt_tokens_details();
                    if(promptDetail == null) {
                        promptDetail = new CompletionResponse.TokensDetail();
                        builder.prompt_tokens_details(promptDetail);
                    }
                    if(Modality.IMAGE.name().equals(detail.getModality())) {
                        promptDetail.setImage_tokens(promptDetail.getImage_tokens() + detail.getTokenCount());
                    } else if(Modality.AUDIO.name().equals(detail.getModality())) {
                        promptDetail.setAudio_tokens(promptDetail.getAudio_tokens() + detail.getTokenCount());
                    }
                }
            });
        }
        
        return builder.build();
    }
    
    private static int calculateCompletionTokens(UsageMetadata usageMetadata) {
        int candidateTokens = usageMetadata.getCandidatesTokenCount() != null ? usageMetadata.getCandidatesTokenCount() : 0;
        int thoughtTokens = usageMetadata.getThoughtsTokenCount() != null ? usageMetadata.getThoughtsTokenCount() : 0;
        return candidateTokens + thoughtTokens;
    }
    
    private static String convertFinishReason(String vertexFinishReason) {
        if (vertexFinishReason == null) {
            return null;
        }

        // tool_calls 在 gemini中也是stop，此处未进行区分
        switch (vertexFinishReason) {
            case "MAX_TOKENS":
                return "length";
            case "SAFETY":
            case "RECITATION":
                return "content_filter";
            default:
                return "stop";
        }
    }
    
    private static Map<String, Object> parseArguments(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return new HashMap<>();
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = JacksonUtils.toMap(arguments);
        return result != null ? result : new HashMap<>();
    }
    
    private static String serializeArguments(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        
        String result = JacksonUtils.serialize(args);
        return StringUtils.hasText(result) ? result : "{}";
    }
}
