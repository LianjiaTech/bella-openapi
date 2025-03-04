package com.ke.bella.openapi.login.config;

import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.login.LogoutFilter;
import com.ke.bella.openapi.login.cas.BellaCasClient;
import com.ke.bella.openapi.login.cas.BellaCasLoginFilter;
import com.ke.bella.openapi.login.cas.BellaRedirectFilter;
import com.ke.bella.openapi.login.cas.BellaValidatorFilter;
import com.ke.bella.openapi.login.cas.CasProperties;
import com.ke.bella.openapi.login.oauth.OAuthLoginFilter;
import com.ke.bella.openapi.login.oauth.OAuthProperties;
import com.ke.bella.openapi.login.oauth.OAuthService;
import com.ke.bella.openapi.login.oauth.providers.GoogleOAuthService;
import com.ke.bella.openapi.login.session.SessionManager;
import com.ke.bella.openapi.login.session.SessionProperty;
import com.ke.bella.openapi.login.user.IUserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class BellaLoginConfiguration {

    public static final String redirectParameter = "redirect";

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("X-Redirect-Login");
        config.addExposedHeader("Location");
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    private RedisTemplate<String, Operator> operatorRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Operator> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Operator.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Operator.class));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConfigurationProperties(value = "bella.session")
    public SessionProperty sessionProperty() {
        return new SessionProperty();
    }

    @Bean
    public SessionManager sessionManager(SessionProperty sessionProperty, @Autowired(required = false) IUserRepo userRepo) {
        SessionManager manager = new SessionManager(sessionProperty, operatorRedisTemplate(redisConnectionFactory), new StringRedisTemplate(redisConnectionFactory));
        if (userRepo != null) {
            manager.setUserRepo(userRepo);
        }
        return manager;
    }

    @Bean
    @ConfigurationProperties(value = "bella.cas")
    public CasProperties casProperties() {
        return new CasProperties();
    }

    @Bean
    @ConditionalOnCasEnable
    public BellaCasClient bellaCasClient(CasProperties casProperties, SessionManager sessionManager) {
        return new BellaCasClient(casProperties, sessionManager);
    }

    @Bean
    @ConditionalOnCasEnable
    public FilterRegistrationBean<BellaValidatorFilter> casValidationFilter(BellaCasClient bellaCasClient) {
        return bellaCasClient.casValidationFilter();
    }

    @Bean
    @ConditionalOnCasEnable
    public FilterRegistrationBean<BellaRedirectFilter> casRedirectFilter(BellaCasClient bellaCasClient) {
        return bellaCasClient.casRedirectRegistrationBean();
    }

    @Bean
    @ConditionalOnCasEnable
    public FilterRegistrationBean<BellaCasLoginFilter> casAuthenticationFilter(BellaCasClient bellaCasClient) {
        return bellaCasClient.casAuthenticationFilter();
    }

    @Bean
    @ConfigurationProperties(value = "bella.oauth")
    public OAuthProperties oauthProperties() {
        return new OAuthProperties();
    }

    @Bean
    @ProviderConditional.ConditionalOnGoogleAuthEnable
    public GoogleOAuthService googleOAuthService(OAuthProperties properties) {
        return new GoogleOAuthService(properties.getProviders().get("google"));
    }

    @Bean
    @ConditionalOnOAuthEnable
    public FilterRegistrationBean<OAuthLoginFilter> oauthLoginFilter(
            List<OAuthService> services,
            SessionManager sessionManager,
            OAuthProperties properties) {
        FilterRegistrationBean<OAuthLoginFilter> registration = new FilterRegistrationBean<>();
        OAuthLoginFilter filter = new OAuthLoginFilter(services, sessionManager, properties);
        registration.setFilter(filter);
        registration.setUrlPatterns(properties.getValidationUrlPatterns());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<LogoutFilter> logoutFilter(SessionManager sessionManager) {
        FilterRegistrationBean<LogoutFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LogoutFilter(sessionManager));
        registration.addUrlPatterns("/logout");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
