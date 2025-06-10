package com.ke.bella.openapi.protocol.completion;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.TokenCounter;
import com.knuddels.jtokkit.api.EncodingType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CompletionLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData processData) {
        long startTime = processData.getRequestTime();
        CompletionResponse response = null;
        if(processData.getResponse() instanceof CompletionResponse) {
            response = (CompletionResponse) processData.getResponse();
        }
        long created = response == null || response.getCreated() <= 0 ? DateTimeUtils.getCurrentSeconds() : response.getCreated();
        long firstPackageTime = processData.getFirstPackageTime();
        CompletionRequest request = (CompletionRequest) processData.getRequest();
        String encodingType = processData.getEncodingType();
        CompletionResponse.TokenUsage usage = countTokenUsage(request, processData.getResponse(), encodingType);
        if (AuthorizationProperty.AuthType.GOOGLE_AUTH.equals(processData.getAuthType())){
            usage = usage.validate();
        }
        processData.setUsage(usage);
        processData.setMetrics(countMetrics(startTime, processData.getRequestMillis(), created, firstPackageTime, usage));
    }

    @Override
    public String endpoint() {
        return "/v1/chat/completions";
    }

    private Map<String, Object> countMetrics(long startTime, long startMills, long endTime, long firstPackageTime, CompletionResponse.TokenUsage usage) {
        int inputToken = usage.getPrompt_tokens();
        int outputToken = usage.getCompletion_tokens();
        int ttft = 0;
        if(firstPackageTime != 0) {
            ttft = (int) (firstPackageTime - startMills);
        }
        int ttlt = (int) (endTime - startTime);
        return ImmutableMap.of("ttft", ttft, "ttlt", ttlt, "input_token", inputToken, "output_token", outputToken);
    }

    private CompletionResponse.TokenUsage countTokenUsage(CompletionRequest request, OpenapiResponse openapiResponse, String encodingType) {
        if(openapiResponse.getError() != null) {
            int httpCode = openapiResponse.getError().getHttpCode();
            if(httpCode > 399 && httpCode < 500 && httpCode != 408) {
                CompletionResponse.TokenUsage tokenUsage = new CompletionResponse.TokenUsage();
                tokenUsage.setPrompt_tokens(0);
                tokenUsage.setCompletion_tokens(0);
                tokenUsage.setTotal_tokens(0);
                return tokenUsage;
            }
        }
        CompletionResponse response = null;
        if(openapiResponse instanceof CompletionResponse) {
            response = (CompletionResponse) openapiResponse;
        }
        if(response != null && response.getUsage() != null) {
            return response.getUsage();
        }
        EncodingType encoding = EncodingType.fromName(encodingType).orElse(EncodingType.CL100K_BASE);
        //计费模型请求消耗量
        //计算非userMessage的token用量
        int requestToken = 0;
        List<String> textMessage = new LinkedList<>();
        List<Pair<String, Boolean>> imgMessage = new LinkedList<>();
        if(request.getMessages() != null) {
            for (Message message : request.getMessages()) {
                if(CollectionUtils.isNotEmpty(message.getTool_calls())) {
                    textMessage.addAll(getToolCallStr(message.getTool_calls()));
                } else {
                    //如果message.getContent()是String类型
                    if(message.getContent() instanceof String) {
                        textMessage.add((String) message.getContent());
                    } else if(message.getContent() instanceof java.util.List) {
                        for (Map content : (java.util.List<Map>) message.getContent()) {
                            if(content.containsKey("text")) {
                                textMessage.add((String) content.get("text"));
                            } else if(content.containsKey("image_url")) {
                                //如果包含类型为string的image_url
                                if(content.get("image_url") instanceof String) {
                                    imgMessage.add(Pair.of((String) content.get("image_url"), false));
                                } else if(content.get("image_url") instanceof Map) {
                                    String url = (String) ((Map) content.get("image_url")).get("url");
                                    boolean lowResolution = "low".equals(((Map) content.get("image_url")).get("detail"));
                                    imgMessage.add(Pair.of(url, lowResolution));
                                }
                            }
                        }
                    }
                }
            }
        }
        Optional<Integer> userTextMessageToken = textMessage.stream().map(x -> TokenCounter.tokenCount(x, encoding)).reduce(Integer::sum);
        Optional<Integer> userImgMessageToken = imgMessage.stream().map(x-> TokenCounter.imageToken(x.getLeft(), x.getRight())).reduce(Integer::sum);
        requestToken += userTextMessageToken.orElse(0) + userImgMessageToken.orElse(0);

        int responseToken = (response == null || response.getChoices() == null) ? 0 : response.getChoices().stream()
                .map(x -> {
                    if(CollectionUtils.isNotEmpty(x.getMessage().getTool_calls())) {
                        return getToolCallStr(x.getMessage().getTool_calls());
                    } else {
                        return Lists.newArrayList(x.getMessage().getContent());
                    }
                }).flatMap(List::stream)
                .map(String.class::cast)
                .map(x -> TokenCounter.tokenCount(x, encoding)).reduce(Integer::sum).orElse(0);
        CompletionResponse.TokenUsage tokenUsage = new CompletionResponse.TokenUsage();
        tokenUsage.setPrompt_tokens(requestToken);
        tokenUsage.setCompletion_tokens(responseToken);
        tokenUsage.setTotal_tokens(requestToken + responseToken);
        return tokenUsage;
    }

    private List<String> getToolCallStr(List<Message.ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(t->getFunctionStr(t.getFunction()))
                .collect(Collectors.toList());
    }
    private String getFunctionStr(Message.FunctionCall functionCall) {
        return functionCall.getName() == null ? functionCall.getArguments() :
                functionCall.getName() + functionCall.getArguments();
    }


}
