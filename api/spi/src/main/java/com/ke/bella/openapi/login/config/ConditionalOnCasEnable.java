package com.ke.bella.openapi.login.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnCasEnable.CasEnableCondition.class)
public @interface ConditionalOnCasEnable {
    class CasEnableCondition extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String loginType = context.getEnvironment().getProperty("bella.login.type");
            String casServerUrlPrefix = context.getEnvironment().getProperty("bella.cas.server-url-prefix");

            boolean isCasEnabled = "cas".equalsIgnoreCase(loginType) && casServerUrlPrefix != null && !casServerUrlPrefix.isEmpty();

            if(isCasEnabled) {
                return ConditionOutcome.match("CAS is enabled");
            } else {
                return ConditionOutcome.noMatch("CAS is not enabled");
            }
        }
    }
}
