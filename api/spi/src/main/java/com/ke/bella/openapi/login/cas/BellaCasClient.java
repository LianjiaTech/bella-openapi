package com.ke.bella.openapi.login.cas;

import com.ke.bella.openapi.login.LoginProperties;
import com.ke.bella.openapi.login.session.SessionManager;
import org.jasig.cas.client.configuration.ConfigurationKeys;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import java.util.HashMap;
import java.util.Map;

public class BellaCasClient {
    private static final int CAS_STEP_ONE = Ordered.HIGHEST_PRECEDENCE + 51;
    private static final int CAS_STEP_TWO = CAS_STEP_ONE + 1;
    private static final int CAS_STEP_THREE = CAS_STEP_TWO + 1;

    private final LoginProperties loginProperties;

    private final CasProperties casProperties;

    private final SessionManager sessionManager;

    public BellaCasClient(LoginProperties loginProperties, CasProperties casProperties, SessionManager sessionManager) {
        this.loginProperties = loginProperties;
        this.casProperties = casProperties;
        this.sessionManager = sessionManager;
    }

    public FilterRegistrationBean<BellaValidatorFilter> casValidationFilter() {
        FilterRegistrationBean<BellaValidatorFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        BellaValidatorFilter targetCasValidationFilter = new BellaValidatorFilter(casProperties.isUseCasUserId(), casProperties.getIdAttribute(),
                casProperties.getNameAttribute(), casProperties.getEmailAttribute(), casProperties.getSource(), casProperties.getOptionalAttributes(), sessionManager);
        filterRegistrationBean.setFilter(targetCasValidationFilter);
        filterRegistrationBean.setOrder(CAS_STEP_ONE);
        Map<String, String> initParams = new HashMap<>();
        initParams.put(ConfigurationKeys.CAS_SERVER_URL_PREFIX.getName(), casProperties.getServerUrlPrefix());
        initParams.put(ConfigurationKeys.SERVER_NAME.getName(), casProperties.getClientHost());
        initParams.put(ConfigurationKeys.REDIRECT_AFTER_VALIDATION.getName(), String.valueOf(Boolean.FALSE));
        filterRegistrationBean.setInitParameters(initParams);
        filterRegistrationBean.getInitParameters().put(ConfigurationKeys.USE_SESSION.getName(), String.valueOf(Boolean.TRUE));
        return filterRegistrationBean;
    }

    public FilterRegistrationBean<BellaRedirectFilter> casRedirectRegistrationBean() {
        FilterRegistrationBean<BellaRedirectFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        BellaRedirectFilter targetCasAuthFilter = new BellaRedirectFilter();
        filterRegistrationBean.setFilter(targetCasAuthFilter);
        filterRegistrationBean.setOrder(CAS_STEP_TWO);
        return filterRegistrationBean;
    }

    public FilterRegistrationBean<BellaCasLoginFilter> casAuthenticationFilter() {
        FilterRegistrationBean<BellaCasLoginFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        BellaCasLoginFilter filter = new BellaCasLoginFilter(casProperties.getServerLoginUrl(), casProperties.getClientHost(),
                casProperties.getClientUri(), casProperties.isClientSupport(), loginProperties.getAuthorizationHeader(),
                casProperties.getClientIndexUrl(), sessionManager);
        filterRegistrationBean.setOrder(CAS_STEP_THREE);
        filterRegistrationBean.setFilter(filter);
        return filterRegistrationBean;
    }
}
