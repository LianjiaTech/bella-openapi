package com.ke.bella.openapi.configuration;

import com.ke.bella.openapi.safety.ISafetyAuditService;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

import static com.ke.bella.openapi.common.EntityConstants.HIGHEST_SAFETY_LEVEL;

@Configuration
public class SafetyConfig {
    
    @Bean
    @ConditionalOnMissingBean(ISafetyCheckService.IChatSafetyCheckService.class)
    public ISafetyCheckService.IChatSafetyCheckService defaultChatSafetyCheckService(
            @Qualifier("taskExecutor") Executor taskExecutor) {
        return new ISafetyCheckService.IChatSafetyCheckService() {
            @Override
            public Object safetyCheck(com.ke.bella.openapi.safety.SafetyCheckRequest.Chat request, boolean isMock) {
                return SafetyCheckResult.builder().status(SafetyCheckResult.Status.passed.name()).build();
            }

            @Override
            public Executor getExecutor() {
                return taskExecutor;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ISafetyAuditService.class)
    public ISafetyAuditService defaultSafetyAuditService()  {
        return certifyCode -> HIGHEST_SAFETY_LEVEL;
    }

}
