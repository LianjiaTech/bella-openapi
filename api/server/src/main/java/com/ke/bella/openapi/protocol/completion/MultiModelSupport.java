package com.ke.bella.openapi.protocol.completion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class for multi-model support functionality
 */
@Component
public class MultiModelSupport {

    @Value("${bella.openapi.max-models-per-request:3}")
    private Integer maxModelsPerRequest;

    /**
     * Validates if the model string contains valid number of models
     */
    public void validateModelCount(String modelString) {
        if (modelString != null && modelString.contains(",")) {
            String[] models = modelString.split(",");
            if (models.length > maxModelsPerRequest) {
                throw new IllegalArgumentException("请求模型数量超过最大限制: " + maxModelsPerRequest);
            }
        }
    }

    /**
     * Returns the maximum number of models allowed per request
     */
    public Integer getMaxModelsPerRequest() {
        return maxModelsPerRequest;
    }
}
