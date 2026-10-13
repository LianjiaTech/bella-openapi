package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.RequestMetrics;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.TokenCalculationUtils;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class RerankLogHandler implements EndpointLogHandler {
    @Override
    public void process(EndpointProcessData processData) {
        String encodingType = processData.getEncodingType();
        RerankResponse response = null;
        if(processData.getResponse() instanceof RerankResponse) {
            response = (RerankResponse) processData.getResponse();
        }
        if(StringUtils.isNotBlank(processData.getResponseRaw()) && response == null) {
            response = JacksonUtils.deserialize(processData.getResponseRaw(), RerankResponse.class);
        }
        if(StringUtils.isNotBlank(processData.getRequestRaw())) {
            RerankRequest request = JacksonUtils.deserialize(processData.getRequestRaw(), RerankRequest.class);
            processData.setRequest(request);
        }

        RerankResponse.Usage usage = getTokenUsage(processData, response, encodingType);

        long startTime = processData.getRequestTime();
        int ttlt = (int) (DateTimeUtils.getCurrentSeconds() - startTime);
        Map<String, Object> map = new HashMap<>();
        map.put("ttlt", ttlt);
        map.put("token", usage.getTotalTokens());
        processData.setMetrics(map);
        processData.setUsage(usage);
    }

    private RerankResponse.Usage getTokenUsage(EndpointProcessData processData, RerankResponse response, String encodingType) {
        if(response != null && response.getUsage() != null && response.getUsage().getTotalTokens() != null) {
            return response.getUsage();
        }

        OpenapiResponse processResponse = processData.getResponse();
        OpenapiResponse.OpenapiError error = processResponse == null ? null : processResponse.getError();
        Integer httpCode = error == null ? null : error.getHttpCode();
        if(httpCode != null && httpCode > 399 && httpCode < 500 && httpCode != 408) {
            return usage(0);
        }

        RequestMetrics metrics = processData.getRequestMetrics();
        if(metrics != null && metrics.getRerankTokens() != null) {
            return usage(metrics.getRerankTokens());
        }

        Object request = processData.getRequest();
        if(request instanceof RerankRequest) {
            EncodingType encoding = EncodingType.fromName(StringUtils.defaultString(encodingType)).orElse(EncodingType.CL100K_BASE);
            return usage(TokenCalculationUtils.calculateRerankTokens((RerankRequest) request, encoding));
        }

        if(request == null) {
            metrics = processData.getRequestMetrics();
            if(metrics != null && metrics.getRerankTokens() != null) {
                return usage(metrics.getRerankTokens());
            }
        }

        log.warn("Unable to calculate rerank tokens, both RequestMetrics and original request are unavailable. RequestId: {}",
                processData.getRequestId());
        return usage(0);
    }

    private RerankResponse.Usage usage(int tokens) {
        RerankResponse.Usage usage = new RerankResponse.Usage();
        usage.setTotalTokens(tokens);
        return usage;
    }

    @Override
    public String endpoint() {
        return "/v1/reranks";
    }
}
