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
@Conditional(ConditionalOnClientEnable.ClientEnableCondition.class)
public @interface ConditionalOnClientEnable {
    class ClientEnableCondition extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String loginType = context.getEnvironment().getProperty("bella.login.type");
            boolean isOAuthEnabled = "client".equalsIgnoreCase(loginType);
            if(isOAuthEnabled) {
                return ConditionOutcome.match("Client is enabled");
            } else {
                return ConditionOutcome.noMatch("Client is not enabled");
            }
        }
    }
}
