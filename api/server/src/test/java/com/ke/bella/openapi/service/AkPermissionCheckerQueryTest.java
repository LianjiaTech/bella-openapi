package com.ke.bella.openapi.service;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.AkOperation;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.ke.bella.openapi.common.EntityConstants.PERSON;
import static com.ke.bella.openapi.common.EntityConstants.HIGH;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AkPermissionCheckerQueryTest {

    @InjectMocks
    private AkPermissionChecker checker;

    @Mock
    private ApikeyRepo apikeyRepo;

    @After
    public void tearDown() {
        BellaContext.clearAll();
        EndpointContext.clearAll();
    }

    @Test
    public void ownerCanQueryPersonalApiKeyAfterManagementDelegation() {
        BellaContext.setOperator(operator(1001L));

        assertThatCode(() -> checker.check(target("1001", "2002"), AkOperation.QUERY))
                .doesNotThrowAnyException();
    }

    @Test
    public void managerCanQueryManagedApiKey() {
        BellaContext.setOperator(operator(2002L));

        assertThatCode(() -> checker.check(target("1001", "2002"), AkOperation.QUERY))
                .doesNotThrowAnyException();
    }

    @Test
    public void managerCanRenameManagedApiKey() {
        BellaContext.setOperator(operator(2002L));

        assertThatCode(() -> checker.check(target("1001", "2002"), AkOperation.RENAME))
                .doesNotThrowAnyException();
    }

    @Test
    public void managerCanBindServiceForManagedApiKey() {
        BellaContext.setOperator(operator(2002L));

        assertThatCode(() -> checker.check(target("1001", "2002"), AkOperation.BIND_SERVICE))
                .doesNotThrowAnyException();
    }

    @Test
    public void highRoleOwnerCanBindServiceForOwnedApiKey() {
        EndpointContext.setApikey(ApikeyInfo.builder()
                .ownerType(PERSON)
                .ownerCode("1001")
                .roleCode(HIGH)
                .build());

        assertThatCode(() -> checker.check(target("1001", ""), AkOperation.BIND_SERVICE))
                .doesNotThrowAnyException();
    }

    @Test
    public void apikeyInfoCheckKeepsParentRelationForPermissionResolution() {
        BellaContext.setOperator(operator(1001L));
        ApikeyInfo child = ApikeyInfo.builder()
                .code("ak-child")
                .parentCode("ak-parent")
                .ownerType("org")
                .ownerCode("org-2")
                .managerCode("2002")
                .build();
        when(apikeyRepo.queryByUniqueKey("ak-parent")).thenReturn(target("1001", ""));

        assertThatCode(() -> checker.check(child, AkOperation.QUERY))
                .doesNotThrowAnyException();
    }

    @Test
    public void unrelatedUserCannotQueryApiKey() {
        BellaContext.setOperator(operator(3003L));

        assertThatThrownBy(() -> checker.check(target("1001", "2002"), AkOperation.QUERY))
                .isInstanceOf(BellaException.AuthorizationException.class)
                .hasMessageContaining("没有操作权限");
    }

    @Test
    public void unrelatedUserCannotRenameApiKey() {
        BellaContext.setOperator(operator(3003L));

        assertThatThrownBy(() -> checker.check(target("1001", "2002"), AkOperation.RENAME))
                .isInstanceOf(BellaException.AuthorizationException.class)
                .hasMessageContaining("没有操作权限");
    }

    private Operator operator(Long userId) {
        return Operator.builder().userId(userId).userName("user-" + userId).build();
    }

    private ApikeyDB target(String ownerCode, String managerCode) {
        ApikeyDB target = new ApikeyDB();
        target.setOwnerType(PERSON);
        target.setOwnerCode(ownerCode);
        target.setManagerCode(managerCode);
        return target;
    }
}
