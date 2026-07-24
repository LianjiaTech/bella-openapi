package com.ke.bella.openapi;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnit4.class)
public class EndpointProcessDataTest {

    @Test
    public void setApikeyInfoCopiesManagerCodeAndExistingAkFields() {
        ApikeyInfo apikeyInfo = ApikeyInfo.builder()
                .apikey("sk-test")
                .code("ak-code")
                .parentCode("parent-ak-code")
                .ownerType("user")
                .ownerCode("owner-code")
                .managerCode("manager-code")
                .build();

        EndpointProcessData processData = new EndpointProcessData();
        processData.setApikeyInfo(apikeyInfo);

        assertThat(processData.getApikey()).isEqualTo("sk-test");
        assertThat(processData.getAkCode()).isEqualTo("ak-code");
        assertThat(processData.getParentAkCode()).isEqualTo("parent-ak-code");
        assertThat(processData.getAccountType()).isEqualTo("user");
        assertThat(processData.getAccountCode()).isEqualTo("owner-code");
        assertThat(processData.getManagerCode()).isEqualTo("manager-code");
    }
}
