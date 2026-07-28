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
    public void updateSubApikeyAcceptsRequestWithoutAnyUpdateField() {
        SubApikeyUpdateOp op = validRequest();
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
        when(apikeyRepo.queryByCode(childCode)).thenReturn(child);
        when(apikeyRepo.queryByCode("ak-parent")).thenReturn(parent);
        when(apikeyRepo.queryByUniqueKey("ak-parent")).thenReturn(new ApikeyDB());
    }
}
