package com.ke.bella.openapi.protocol.completion;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.RequestMetrics;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.TokenCalculationUtils;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class CompletionLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData processData) {
        long startTime = processData.getRequestTime();
        CompletionResponse response = null;
        if(processData.getResponse() instanceof CompletionResponse) {
            response = (CompletionResponse) processData.getResponse();
        }

        if(StringUtils.isNotBlank(processData.getResponseRaw()) && processData.getResponse() == null) {
            response = JacksonUtils.deserialize(processData.getResponseRaw(), CompletionResponse.class);
        }
        if(StringUtils.isNotBlank(processData.getRequestRaw())) {
            CompletionRequest request = JacksonUtils.deserialize(processData.getRequestRaw(), CompletionRequest.class);
            processData.setRequest(request);
        }
        long created = response == null || response.getCreated() <= 0 ? DateTimeUtils.getCurrentSeconds() : response.getCreated();
        long firstPackageTime = processData.getFirstPackageTime();
        String encodingType = processData.getEncodingType();
        CompletionResponse.TokenUsage usage;
        if(response == null || response.getUsage() == null) {
            // 分别计算request和response的token
            int inputTokens = calculateInputTokens(processData, encodingType);
            int outputTokens = calculateOutputTokens(response, encodingType);

            // 构建TokenUsage
            usage = new CompletionResponse.TokenUsage();
            usage.setPrompt_tokens(inputTokens);
            usage.setCompletion_tokens(outputTokens);
            usage.setTotal_tokens(inputTokens + outputTokens);
        } else {
            usage = response.getUsage();
        }

        processData.setUsage(usage);
        processData.setMetrics(countMetrics(startTime, processData.getRequestMillis(), created, firstPackageTime, usage));
        if(response != null && response.getChoices() != null) {
            response.getChoices().forEach(choice -> choice.setLogprobs(null));
        }
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

    /**
     * 计算输入token数量
     */
    private int calculateInputTokens(EndpointProcessData processData, String encodingType) {
        // 1. 检查错误响应 - 4xx错误不计算输入token
        if(processData.getResponse() != null && processData.getResponse().getError() != null) {
            int httpCode = processData.getResponse().getError().getHttpCode();
            if(httpCode > 399 && httpCode < 500 && httpCode != 408) {
                return 0;
            }
        }

        // 2. 优先使用预计算的RequestMetrics
        RequestMetrics metrics = processData.getRequestMetrics();
        if (metrics != null && metrics.getInputTokens() != null) {
            log.debug("Using pre-calculated inputTokens: {}", metrics.getInputTokens());
            return metrics.getInputTokens();
        }

        // 3. 尝试从原始request计算
        Object request = processData.getRequest();
        if (request instanceof CompletionRequest) {
            EncodingType encoding = EncodingType.fromName(encodingType).orElse(EncodingType.CL100K_BASE);
            int tokens = TokenCalculationUtils.calculateCompletionInputTokens((CompletionRequest) request, encoding);
            log.debug("Calculated inputTokens from original request: {}", tokens);
            return tokens;
        }

        // 4. Double check: 如果request是null，再检查一次metrics
        if (request == null) {
            metrics = processData.getRequestMetrics();
            if (metrics != null && metrics.getInputTokens() != null) {
                log.debug("Got inputTokens from metrics on double-check: {}", metrics.getInputTokens());
                return metrics.getInputTokens();
            }
        }

        log.warn("Unable to calculate input tokens. RequestId: {}", processData.getRequestId());
        return 0;
    }

    /**
     * 计算输出token数量
     */
    private int calculateOutputTokens(CompletionResponse response, String encodingType) {
        if (response != null && response.getChoices() != null) {
            EncodingType encoding = EncodingType.fromName(encodingType).orElse(EncodingType.CL100K_BASE);
            int tokens = TokenCalculationUtils.calculateCompletionOutputTokens(response, encoding);
            log.debug("Calculated outputTokens from response choices: {}", tokens);
            return tokens;
        }

        log.debug("No response data available for output token calculation");
        return 0;
    }

}
