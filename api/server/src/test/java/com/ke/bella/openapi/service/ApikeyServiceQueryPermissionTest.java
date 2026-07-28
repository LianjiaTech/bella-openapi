package com.ke.bella.openapi.service;

import com.ke.bella.openapi.apikey.AkOperation;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.ApikeyBrief;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
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
}
