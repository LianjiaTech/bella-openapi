package com.ke.bella.openapi.protocol.embedding;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.RequestMetrics;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.TokenCalculationUtils;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EmbeddingLogHandler implements EndpointLogHandler {
    @Override
    public void process(EndpointProcessData processData) {
        String encodingType = processData.getEncodingType();
        EmbeddingResponse response = null;
        if(processData.getResponse() instanceof EmbeddingResponse) {
            response = (EmbeddingResponse) processData.getResponse();
        }

        // 获取usage - 优先使用预计算的值
        EmbeddingResponse.TokenUsage usage = getTokenUsage(processData, response, encodingType);

        long startTime = processData.getRequestTime();
        int ttlt = (int) (DateTimeUtils.getCurrentSeconds() - startTime);
        Map<String, Object> map = new HashMap<>();
        map.put("ttlt", ttlt);
        map.put("token", usage.getTotal_tokens());
        processData.setMetrics(map);
        processData.setUsage(usage);
    }

    /**
     * 获取token使用量 - 优先使用RequestMetrics中预计算的值
     */
    private EmbeddingResponse.TokenUsage getTokenUsage(EndpointProcessData processData,
                                                       EmbeddingResponse response,
                                                       String encodingType) {
        // 1. 优先使用response中的usage
        if(response != null && response.getUsage() != null) {
            return response.getUsage();
        }

        // 2. 检查是否为错误响应
        OpenapiResponse.OpenapiError error = processData.getResponse().getError();
        if(error != null && error.getHttpCode() > 399 && error.getHttpCode() < 500 && error.getHttpCode() != 408) {
            EmbeddingResponse.TokenUsage tokenUsage = new EmbeddingResponse.TokenUsage();
            tokenUsage.setPrompt_tokens(0);
            tokenUsage.setTotal_tokens(0);
            return tokenUsage;
        }

        // 3. 使用预计算的RequestMetrics
        RequestMetrics metrics = processData.getRequestMetrics();
        if (metrics != null && metrics.getEmbeddingTokens() != null) {
            EmbeddingResponse.TokenUsage tokenUsage = new EmbeddingResponse.TokenUsage();
            tokenUsage.setPrompt_tokens(metrics.getEmbeddingTokens());
            tokenUsage.setTotal_tokens(metrics.getEmbeddingTokens());
            return tokenUsage;
        }

        // 4. 尝试从原始request计算
        Object request = processData.getRequest();
        if (request instanceof EmbeddingRequest) {
            EncodingType encoding = EncodingType.fromName(encodingType).orElse(EncodingType.CL100K_BASE);
            int inputToken = TokenCalculationUtils.calculateEmbeddingTokens((EmbeddingRequest) request, encoding);
            EmbeddingResponse.TokenUsage tokenUsage = new EmbeddingResponse.TokenUsage();
            tokenUsage.setPrompt_tokens(inputToken);
            tokenUsage.setTotal_tokens(inputToken);
            return tokenUsage;
        }

        // 5. Double check: 如果request是null，再检查一次metrics
        if (request == null) {
            metrics = processData.getRequestMetrics(); // 再检查一次
            if (metrics != null && metrics.getEmbeddingTokens() != null) {
                log.debug("Got embeddingTokens from metrics on double-check: {}", metrics.getEmbeddingTokens());
                EmbeddingResponse.TokenUsage tokenUsage = new EmbeddingResponse.TokenUsage();
                tokenUsage.setPrompt_tokens(metrics.getEmbeddingTokens());
                tokenUsage.setTotal_tokens(metrics.getEmbeddingTokens());
                return tokenUsage;
            }
        }

        log.warn("Unable to calculate tokens, both RequestMetrics and original request are unavailable. RequestId: {}",
                 processData.getRequestId());
        int inputToken = 0;

        EmbeddingResponse.TokenUsage tokenUsage = new EmbeddingResponse.TokenUsage();
        tokenUsage.setPrompt_tokens(inputToken);
        tokenUsage.setTotal_tokens(inputToken);
        return tokenUsage;
    }


    @Override
    public String endpoint() {
        return "/v1/embeddings";
    }
}
