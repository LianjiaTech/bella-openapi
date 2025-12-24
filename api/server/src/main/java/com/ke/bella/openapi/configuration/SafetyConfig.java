package com.ke.bella.openapi.configuration;

import com.ke.bella.openapi.safety.ISafetyAuditService;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.ke.bella.openapi.common.EntityConstants.HIGHEST_SAFETY_LEVEL;

@Configuration
public class SafetyConfig {
    
    @Bean
    @ConditionalOnMissingBean(ISafetyCheckService.IChatSafetyCheckService.class)
    public ISafetyCheckService.IChatSafetyCheckService defaultChatSafetyCheckService() {
        return (request, isMock) -> SafetyCheckResult.builder().status(SafetyCheckResult.Status.passed.name()).build();
    }

    @Bean
    @ConditionalOnMissingBean(ISafetyAuditService.class)
    public ISafetyAuditService defaultSafetyAuditService()  {
        return certifyCode -> HIGHEST_SAFETY_LEVEL;
    }

}
