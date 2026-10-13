package com.ke.bella.openapi.service;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.SubApikeyUpdateOp;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
import com.ke.bella.openapi.db.repo.ApikeyRoleRepo;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApikeyServiceSubApikeyUpdateTest {

    @InjectMocks
    private ApikeyService service;

    @Mock
    private ApikeyRepo apikeyRepo;

    @Mock
    private ApikeyRoleRepo apikeyRoleRepo;

    @Mock
    private AkPermissionChecker akPermissionChecker;

    @Test
    public void updateSubApikeyAllowsChildManagerToUpdateRemark() {
        SubApikeyUpdateOp op = validRequest();
        op.setRemark("");
        stubChildAndParent(op.getCode());

        boolean updated = service.updateSubApikey(op);

        assertThat(updated).isTrue();
        verify(apikeyRepo).updateSubApikeyFields(op);
    }

    @Test
    public void updateSubApikeyRejectsUnknownRoleCodeBeforeWriting() {
        SubApikeyUpdateOp op = validRequest();
        op.setRoleCode("missing-role");
        stubChildAndParent(op.getCode());
        doThrow(new IllegalArgumentException("实体不存在"))
                .when(apikeyRoleRepo).checkExist("missing-role", true);

        assertThatThrownBy(() -> service.updateSubApikey(op))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("实体不存在");

        verify(apikeyRoleRepo).checkExist("missing-role", true);
        verify(apikeyRepo, never()).updateSubApikeyFields(op);
    }

    @Test
    public void updateSubApikeyAllowsManagedTopLevelApikey() {
        SubApikeyUpdateOp op = validRequest();
        op.setCode("ak-parent");
        op.setRemark("");
        ApikeyInfo parent = ApikeyInfo.builder()
                .code("ak-parent")
                .status("active")
                .safetyLevel((byte) 40)
                .parentCode("")
                .build();
        when(apikeyRepo.queryByCode("ak-parent")).thenReturn(parent);
        when(apikeyRepo.queryByUniqueKey("ak-parent")).thenReturn(new ApikeyDB());

        boolean updated = service.updateSubApikey(op);

        assertThat(updated).isTrue();
        verify(akPermissionChecker).check(org.mockito.ArgumentMatchers.any(ApikeyDB.class), org.mockito.ArgumentMatchers.eq(com.ke.bella.openapi.apikey.AkOperation.CREATE_CHILD));
        verify(apikeyRepo).updateSubApikeyFields(op);
    }

    @Test
    public void updateSubApikeyRejectsTopLevelNonRemarkFields() {
        SubApikeyUpdateOp op = validRequest();
        op.setCode("ak-parent");
        op.setName("should-not-change");
        when(apikeyRepo.queryByCode("ak-parent")).thenReturn(ApikeyInfo.builder().code("ak-parent").build());

        assertThatThrownBy(() -> service.updateSubApikey(op))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持修改备注");
        verify(apikeyRepo, never()).updateSubApikeyFields(op);
    }

    @Test
    public void updateSubApikeyDoesNotWriteWhenTopLevelPermissionIsDenied() {
        SubApikeyUpdateOp op = validRequest();
        op.setCode("ak-parent");
        op.setRemark("new remark");
        when(apikeyRepo.queryByCode("ak-parent")).thenReturn(ApikeyInfo.builder().code("ak-parent").build());
        when(apikeyRepo.queryByUniqueKey("ak-parent")).thenReturn(new ApikeyDB());
        doThrow(new IllegalArgumentException("没有操作权限"))
                .when(akPermissionChecker).check(org.mockito.ArgumentMatchers.any(ApikeyDB.class), org.mockito.ArgumentMatchers.any(com.ke.bella.openapi.apikey.AkOperation.class));

        assertThatThrownBy(() -> service.updateSubApikey(op))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有操作权限");
        verify(apikeyRepo, never()).updateSubApikeyFields(op);
    }

    @Test
    public void updateSubApikeyRejectsUnrelatedChildUser() {
        SubApikeyUpdateOp op = validRequest();
        op.setRemark("new remark");
        stubChildAndParent(op.getCode());
        doThrow(new IllegalArgumentException("没有操作权限"))
                .when(akPermissionChecker).check(org.mockito.ArgumentMatchers.any(ApikeyDB.class), org.mockito.ArgumentMatchers.any(com.ke.bella.openapi.apikey.AkOperation.class));

        assertThatThrownBy(() -> service.updateSubApikey(op))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有操作权限");
        verify(apikeyRepo, never()).updateSubApikeyFields(op);
    }

    @Test
    public void updateSubApikeyWritesProvidedBasicField() {
        SubApikeyUpdateOp op = validRequest();
        op.setName("new-name");
        stubChildAndParent(op.getCode());

        boolean updated = service.updateSubApikey(op);

        assertThat(updated).isTrue();
        verify(apikeyRepo).updateSubApikeyFields(op);
    }

    private SubApikeyUpdateOp validRequest() {
        SubApikeyUpdateOp op = new SubApikeyUpdateOp();
        op.setCode("ak-child");
        return op;
    }

    private void stubChildAndParent(String childCode) {
        ApikeyInfo child = ApikeyInfo.builder()
                .code(childCode)
                .parentCode("ak-parent")
                .build();
        ApikeyInfo parent = ApikeyInfo.builder()
                .code("ak-parent")
                .status("active")
                .safetyLevel((byte) 40)
                .build();
        ApikeyDB childDb = new ApikeyDB();
        childDb.setCode(childCode);
        when(apikeyRepo.queryByCode(childCode)).thenReturn(child);
        when(apikeyRepo.queryByCode("ak-parent")).thenReturn(parent);
        when(apikeyRepo.queryByUniqueKey(childCode)).thenReturn(childDb);
    }
}
