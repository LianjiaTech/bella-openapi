package com.ke.bella.openapi.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.openapi.auth.ClientSecretAuthenticator;
import com.ke.bella.openapi.auth.ClientSecretProperties;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.request.BellaRequestFilter;

public class BellaServiceConfigurationClientSecretTest {

    @Test
    public void defaultRequestFilterUsesClientSecretAuthenticator() {
        BellaServiceConfiguration configuration = new BellaServiceConfiguration();
        OpenapiProperties properties = new OpenapiProperties();
        properties.setService("test-service");

        FilterRegistrationBean<BellaRequestFilter> registration = configuration.bellaRequestFilter(
                mock(OpenapiClient.class), properties, new ClientSecretProperties());

        Object authenticator = ReflectionTestUtils.getField(registration.getFilter(), "authenticator");
        assertThat(authenticator).isInstanceOf(ClientSecretAuthenticator.class);
    }
}
