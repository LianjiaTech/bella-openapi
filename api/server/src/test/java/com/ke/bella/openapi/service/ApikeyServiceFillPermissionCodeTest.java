package com.ke.bella.openapi.service;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.PermissionCondition;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.ke.bella.openapi.common.EntityConstants.ORG;
import static com.ke.bella.openapi.common.EntityConstants.PERSON;
import static com.ke.bella.openapi.common.EntityConstants.PROJECT;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnit4.class)
public class ApikeyServiceFillPermissionCodeTest {

    private final ApikeyService service = new ApikeyService();

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void personApiKeySetsPersonalPermissionScope() {
        EndpointContext.setApikey(apikey(PERSON, "person-a"));
        PermissionCondition condition = new PermissionCondition();

        service.fillPermissionCode(condition, true);

        assertThat(condition.getPersonalCode()).isEqualTo("person-a");
        assertThat(condition.getOrgCodes()).isEmpty();
        assertThat(condition.getProjectCode()).isNull();
    }

    @Test
    public void orgApiKeySetsOrgPermissionScope() {
        EndpointContext.setApikey(apikey(ORG, "org-a"));
        PermissionCondition condition = new PermissionCondition();

        service.fillPermissionCode(condition, true);

        assertThat(condition.getPersonalCode()).isNull();
        assertThat(condition.getOrgCodes()).containsExactly("org-a");
        assertThat(condition.getProjectCode()).isNull();
    }

    @Test
    public void projectApiKeySetsProjectPermissionScope() {
        EndpointContext.setApikey(apikey(PROJECT, "project-a"));
        PermissionCondition condition = new PermissionCondition();

        service.fillPermissionCode(condition, true);

        assertThat(condition.getPersonalCode()).isNull();
        assertThat(condition.getOrgCodes()).isEmpty();
        assertThat(condition.getProjectCode()).isEqualTo("project-a");
    }

    private ApikeyInfo apikey(String ownerType, String ownerCode) {
        return ApikeyInfo.builder()
                .apikey("real-secret-value")
                .ownerType(ownerType)
                .ownerCode(ownerCode)
                .build();
    }
}
