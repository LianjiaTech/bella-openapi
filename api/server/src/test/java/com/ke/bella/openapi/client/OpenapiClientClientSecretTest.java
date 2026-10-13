package com.ke.bella.openapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.junit.Test;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;

public class OpenapiClientClientSecretTest {

    @Test
    public void clientSecretsAreNotAuthenticatedCentrally() {
        OpenapiClient client = new OpenapiClient("http://openapi.invalid");

        assertThat(client.authenticate("Bearer csak-eyJhbGciOiJSUzI1NiJ9.e30.sig", "/v1/audio/transcriptions")).isNull();
    }

    @Test
    public void longLivedApiKeysStillCheckRequestedEndpoint() {
        ApikeyInfo.RolePath rolePath = new ApikeyInfo.RolePath();
        rolePath.getIncluded().add("/v1/audio/**");
        ApikeyInfo apikeyInfo = ApikeyInfo.builder().code("ak-code").rolePath(rolePath).build();
        OpenapiClient client = spy(new OpenapiClient("http://openapi.invalid"));
        doReturn(apikeyInfo).when(client).whoami("sk-test");

        assertThat(client.authenticate("Bearer sk-test", "/v1/audio/speech").getApikeyInfo()).isSameAs(apikeyInfo);
        assertThatThrownBy(() -> client.authenticate("Bearer sk-test", "/v1/images/generations"))
                .isInstanceOf(BellaException.AuthorizationException.class);
    }
}
