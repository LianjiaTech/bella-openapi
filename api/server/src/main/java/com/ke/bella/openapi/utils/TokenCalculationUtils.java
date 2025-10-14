package com.ke.bella.openapi.utils;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.embedding.EmbeddingRequest;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Token计算工具类 - 统一管理各种token计算逻辑
 */
@Slf4j
public class TokenCalculationUtils {

    /**
     * 计算Completion请求的输入token数量
     * 使用与CompletionLogHandler相同的计算逻辑
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int calculateCompletionInputTokens(CompletionRequest request, EncodingType encoding) {
        if (request == null || request.getMessages() == null) {
            return 0;
        }

        int requestToken = 0;
        List<String> textMessage = new LinkedList<>();
        List<Pair<String, Boolean>> imgMessage = new LinkedList<>();

        for (Message message : request.getMessages()) {
            if (CollectionUtils.isNotEmpty(message.getTool_calls())) {
                textMessage.addAll(getToolCallStr(message.getTool_calls()));
            } else {
                // 如果message.getContent()是String类型
                if (message.getContent() instanceof String) {
                    textMessage.add((String) message.getContent());
                } else if (message.getContent() instanceof java.util.List) {
                    for (Map content : (java.util.List<Map>) message.getContent()) {
                        if (content.containsKey("text")) {
                            textMessage.add((String) content.get("text"));
                        } else if (content.containsKey("image_url")) {
                            // 如果包含类型为string的image_url
                            if (content.get("image_url") instanceof String) {
                                imgMessage.add(Pair.of((String) content.get("image_url"), false));
                            } else if (content.get("image_url") instanceof Map) {
                                String url = (String) ((Map) content.get("image_url")).get("url");
                                boolean lowResolution = "low".equals(((Map) content.get("image_url")).get("detail"));
                                imgMessage.add(Pair.of(url, lowResolution));
                            }
                        }
                    }
                }
            }
        }

        Optional<Integer> userTextMessageToken = textMessage.stream()
            .map(x -> TokenCounter.tokenCount(x, encoding))
            .reduce(Integer::sum);
        Optional<Integer> userImgMessageToken = imgMessage.stream()
            .map(x -> TokenCounter.imageToken(x.getLeft(), x.getRight()))
            .reduce(Integer::sum);
        requestToken += userTextMessageToken.orElse(0) + userImgMessageToken.orElse(0);

        return requestToken;
    }

    /**
     * 计算Completion响应的输出token数量
     */
    public static int calculateCompletionOutputTokens(CompletionResponse response, EncodingType encoding) {
        if (response == null || response.getChoices() == null) {
            return 0;
        }

        return response.getChoices().stream()
                .map(x -> {
                    if (CollectionUtils.isNotEmpty(x.getMessage().getTool_calls())) {
                        return getToolCallStr(x.getMessage().getTool_calls());
                    } else {
                        return Lists.newArrayList(x.getMessage().getContent());
                    }
                }).flatMap(List::stream)
                .map(String.class::cast)
                .map(x -> TokenCounter.tokenCount(x, encoding))
                .reduce(Integer::sum).orElse(0);
    }

    /**
     * 计算Embedding请求的token数量
     */
    @SuppressWarnings("unchecked")
    public static int calculateEmbeddingTokens(EmbeddingRequest request, EncodingType encoding) {
        if (request == null || request.getInput() == null) {
            return 0;
        }

        try {
            if (request.getInput() instanceof String) {
                return TokenCounter.tokenCount((String) request.getInput(), encoding);
            } else if (request.getInput() instanceof List) {
                List<String> inputList = (List<String>) request.getInput();
                return inputList.stream()
                    .mapToInt(input -> TokenCounter.tokenCount(input, encoding))
                    .sum();
            }
        } catch (Exception e) {
            log.warn("Failed to calculate embedding tokens", e);
        }

        return 0;
    }

    /**
     * 获取Tool Call字符串列表
     */
    private static List<String> getToolCallStr(List<Message.ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(t -> getFunctionStr(t.getFunction()))
                .collect(Collectors.toList());
    }

    /**
     * 获取Function字符串
     */
    private static String getFunctionStr(Message.FunctionCall functionCall) {
        return functionCall.getName() == null ? functionCall.getArguments() :
                functionCall.getName() + functionCall.getArguments();
    }
}