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

public class ProviderConditional {
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ConditionalOnOAuthEnable
    @Conditional(ConditionalOnGoogleAuthEnable.GoogleAuthEnableCondition.class)
    public @interface ConditionalOnGoogleAuthEnable {
        class GoogleAuthEnableCondition extends SpringBootCondition {
            @Override
            public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
                String googleEnabled = context.getEnvironment().getProperty("bella.oauth.providers.google.enabled");

                boolean isEnabled = Boolean.parseBoolean(googleEnabled);

                if(isEnabled) {
                    return ConditionOutcome.match("Google OAuth is enabled");
                } else {
                    return ConditionOutcome.noMatch("Google OAuth is not enabled");
                }
            }
        }
    }

    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @ConditionalOnOAuthEnable
    @Conditional(ConditionalOnGithubAuthEnable.GithubAuthEnableCondition.class)
    public @interface ConditionalOnGithubAuthEnable {
        class GithubAuthEnableCondition extends SpringBootCondition {
            @Override
            public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
                String googleEnabled = context.getEnvironment().getProperty("bella.oauth.providers.github.enabled");

                boolean isEnabled = Boolean.parseBoolean(googleEnabled);

                if(isEnabled) {
                    return ConditionOutcome.match("Github OAuth is enabled");
                } else {
                    return ConditionOutcome.noMatch("Github OAuth is not enabled");
                }
            }
        }
    }
}
