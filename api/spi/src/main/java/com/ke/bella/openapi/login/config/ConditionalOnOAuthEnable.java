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
@Conditional(ConditionalOnOAuthEnable.OAuthEnableCondition.class)
public @interface ConditionalOnOAuthEnable {
    class OAuthEnableCondition extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String loginType = context.getEnvironment().getProperty("bella.login.type");
            String loginPageUrl = context.getEnvironment().getProperty("bella.login.login-page-url");

            boolean isOAuthEnabled = "oauth".equalsIgnoreCase(loginType) && loginPageUrl != null && !loginPageUrl.isEmpty();

            if(isOAuthEnabled) {
                return ConditionOutcome.match("OAuth is enabled");
            } else {
                return ConditionOutcome.noMatch("OAuth is not enabled");
            }
        }
    }
}
