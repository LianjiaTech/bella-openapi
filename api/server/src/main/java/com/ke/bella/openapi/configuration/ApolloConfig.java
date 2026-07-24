package com.ke.bella.openapi.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;

@Configuration
@ConditionalOnProperty(name = "apollo.enabled", havingValue = "true", matchIfMissing = false)
@EnableApolloConfig
public class ApolloConfig {
}
