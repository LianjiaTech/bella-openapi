package com.ke.bella.openapi.service;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.AkOperation;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.ApikeyBrief;
import com.ke.bella.openapi.apikey.ApikeyOps;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
import com.ke.bella.openapi.db.repo.UserRepo;
import com.ke.bella.openapi.tables.pojos.UserDB;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApikeyServiceQueryPermissionTest {

    @InjectMocks
    private ApikeyService service;

    @Mock
    private ApikeyRepo apikeyRepo;

    @Mock
    private AkPermissionChecker akPermissionChecker;

    @Mock
    private UserRepo userRepo;

    @After
    public void tearDown() {
        BellaContext.clearAll();
    }

    @Test
    public void permissionAwareCodeQueryChecksQueryOperation() {
        ApikeyInfo expected = ApikeyInfo.builder()
                .code("ak-person")
                .status("active")
                .ownerType("person")
                .ownerCode("1001")
                .managerCode("2002")
                .build();
        when(apikeyRepo.queryByCode("ak-person")).thenReturn(expected);

        ApikeyInfo actual = service.queryByCodeWithPermission("ak-person", false);

        assertThat(actual).isSameAs(expected);
        verify(akPermissionChecker).check(expected, AkOperation.QUERY);
    }

    @Test
    public void parentQuotaInfoUsesChildManagerPermissionInsteadOfParentPermission() {
        ApikeyInfo child = ApikeyInfo.builder()
                .code("ak-child")
                .status("active")
                .parentCode("ak-parent")
                .ownerType("org")
                .ownerCode("org-1")
                .managerCode("2002")
                .build();
        ApikeyInfo parent = ApikeyInfo.builder()
                .code("ak-parent")
                .status("active")
                .name("组织父 AK")
                .ownerType("org")
                .ownerCode("org-1")
                .managerCode("3003")
                .managerName("父 AK 管理人")
                .build();
        when(apikeyRepo.queryByCode("ak-child")).thenReturn(child);
        when(apikeyRepo.queryByCode("ak-parent")).thenReturn(parent);

        ApikeyBrief actual = service.queryParentQuotaInfoForChild("ak-child");

        assertThat(actual.getCode()).isEqualTo("ak-parent");
        assertThat(actual.getOwnerType()).isEqualTo("org");
        assertThat(actual.getManagerCode()).isEqualTo("3003");
        verify(akPermissionChecker).check(child, AkOperation.QUERY);
        verify(akPermissionChecker, never()).check(parent, AkOperation.QUERY);
    }

    @Test
    public void parentQuotaInfoRejectsTopLevelApiKey() {
        ApikeyInfo parent = ApikeyInfo.builder()
                .code("ak-parent")
                .status("active")
                .parentCode("")
                .build();
        when(apikeyRepo.queryByCode("ak-parent")).thenReturn(parent);

        assertThatThrownBy(() -> service.queryParentQuotaInfoForChild("ak-parent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只支持查询子AK");
    }

    @Test
    public void parentQuotaInfoReportsMissingOrInactiveParent() {
        ApikeyInfo child = ApikeyInfo.builder()
                .code("ak-child")
                .status("active")
                .parentCode("ak-parent")
                .build();
        when(apikeyRepo.queryByCode("ak-child")).thenReturn(child);
        when(apikeyRepo.queryByCode("ak-parent")).thenReturn(null);

        assertThatThrownBy(() -> service.queryParentQuotaInfoForChild("ak-child"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("父AK不存在或已停用");
    }

    @Test
    public void ownerOrManagerQueryUsesDatabaseUserIdForOauthUser() {
        Operator operator = Operator.builder()
                .userId(1001L)
                .userName("user-1001")
                .sourceId("cas-1001")
                .build();
        UserDB currentUser = new UserDB();
        currentUser.setId(1001L);
        currentUser.setSourceId("cas-1001");
        BellaContext.setOperator(operator);
        when(userRepo.queryById(1001L)).thenReturn(currentUser);
        when(akPermissionChecker.hasAdminPermission()).thenReturn(false);
        ApikeyOps.ApikeyCondition condition = new ApikeyOps.ApikeyCondition();
        condition.setOwnerOrManagerCode("other-user");

        service.pageApikey(condition);

        assertThat(condition.getOwnerOrManagerCode()).isEqualTo("1001");
        assertThat(condition.getPersonalCode()).isNull();
        verify(apikeyRepo).pageAccessKeys(condition);
    }

    @Test
    public void ownerOrManagerQueryPreservesExplicitCodeForAdmin() {
        BellaContext.setOperator(Operator.builder().userId(1001L).userName("admin").build());
        when(akPermissionChecker.hasAdminPermission()).thenReturn(true);
        ApikeyOps.ApikeyCondition condition = new ApikeyOps.ApikeyCondition();
        condition.setOwnerOrManagerCode("target-user");

        service.pageApikey(condition);

        assertThat(condition.getOwnerOrManagerCode()).isEqualTo("target-user");
        assertThat(condition.getPersonalCode()).isNull();
        verify(apikeyRepo).pageAccessKeys(condition);
        verify(userRepo, never()).queryById(1001L);
    }
}
