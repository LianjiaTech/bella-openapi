package com.ke.bella.openapi.configuration;

import com.ke.bella.openapi.safety.ISafetyAuditService;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.safety.SafetyCheckResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

import static com.ke.bella.openapi.common.EntityConstants.HIGHEST_SAFETY_LEVEL;

@Configuration
public class SafetyConfig {

    @Bean
    @ConditionalOnMissingBean(ISafetyCheckService.IChatSafetyCheckService.class)
    public ISafetyCheckService.IChatSafetyCheckService defaultChatSafetyCheckService() {
        return (request, isMock) -> passedResult();
    }

    @Bean
    @ConditionalOnMissingBean(ISafetyCheckService.IImageSafetyCheckService.class)
    public ISafetyCheckService.IImageSafetyCheckService defaultImageSafetyCheckService() {
        return new ISafetyCheckService.IImageSafetyCheckService() {
            @Override
            public Object safetyCheck(SafetyCheckRequest.Image request, boolean isMock) {
                return passedResult();
            }

            @Override
            public Supplier<Object> prepareAsyncCheck(SafetyCheckRequest.Image request, boolean isMock) {
                return SafetyConfig.this::passedResult;
            }
        };
    }

    private SafetyCheckResult passedResult() {
        return SafetyCheckResult.builder().status(SafetyCheckResult.Status.passed.name()).build();
    }

    @Bean
    @ConditionalOnMissingBean(ISafetyAuditService.class)
    public ISafetyAuditService defaultSafetyAuditService() {
        return certifyCode -> HIGHEST_SAFETY_LEVEL;
    }

}
