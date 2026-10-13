package com.ke.bella.openapi.service;

import com.ke.bella.openapi.apikey.ApikeyCreateOp;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.apikey.ApikeyOps;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static com.ke.bella.openapi.common.EntityConstants.ORG;
import static com.ke.bella.openapi.common.EntityConstants.PERSON;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApikeyServicePersonalParentLimitTest {

    @InjectMocks
    private ApikeyService service;

    @Mock
    private ApikeyRepo apikeyRepo;

    @Mock
    private AkPermissionChecker akPermissionChecker;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(service, "personalParentLimit", 10);
    }

    @Test
    public void defaultLimitAllowsCreationBelowTen() {
        ApikeyOps.ApplyOp op = personalApplyOp();
        when(apikeyRepo.countActivePersonalParents("person-1")).thenReturn(9);

        service.applyForPerson(op);

        verify(apikeyRepo).insert(any(ApikeyDB.class));
    }

    @Test
    public void defaultLimitBlocksCreationAtTen() {
        ApikeyOps.ApplyOp op = personalApplyOp();
        when(apikeyRepo.countActivePersonalParents("person-1")).thenReturn(10);

        assertThatThrownBy(() -> service.applyForPerson(op))
                .isInstanceOf(BizParamCheckException.class)
                .hasMessageContaining("10个");

        verify(apikeyRepo, never()).insert(any(ApikeyDB.class));
    }

    @Test
    public void configuredLimitOverridesDefault() {
        ReflectionTestUtils.setField(service, "personalParentLimit", 2);
        ApikeyOps.ApplyOp op = personalApplyOp();
        when(apikeyRepo.countActivePersonalParents("person-1")).thenReturn(2);

        assertThatThrownBy(() -> service.applyForPerson(op))
                .isInstanceOf(BizParamCheckException.class)
                .hasMessageContaining("2个");

        verify(apikeyRepo, never()).insert(any(ApikeyDB.class));
    }

    @Test
    public void inactiveParentReleasesSlot() {
        ReflectionTestUtils.setField(service, "personalParentLimit", 1);
        ApikeyOps.ApplyOp op = personalApplyOp();
        when(apikeyRepo.countActivePersonalParents("person-1")).thenReturn(0);

        service.applyForPerson(op);

        verify(apikeyRepo).insert(any(ApikeyDB.class));
    }

    @Test
    public void nonPersonalParentCreationDoesNotCheckPersonalLimit() {
        ApikeyOps.ApplyOp op = ApikeyOps.ApplyOp.builder()
                .ownerType(ORG)
                .ownerCode("org-1")
                .ownerName("org")
                .managerCode("manager-1")
                .managerName("manager")
                .build();
        when(akPermissionChecker.hasAdminPermission()).thenReturn(true);

        service.applyForNonPerson(op);

        verify(apikeyRepo, never()).countActivePersonalParents(any());
        verify(apikeyRepo).insert(any(ApikeyDB.class));
    }

    @Test
    public void childCreationDoesNotCheckPersonalLimit() {
        ApikeyInfo parent = ApikeyInfo.builder()
                .code("ak-parent")
                .parentCode("")
                .ownerType(PERSON)
                .ownerCode("person-1")
                .ownerName("person")
                .status("active")
                .monthQuota(BigDecimal.TEN)
                .safetyLevel((byte) 40)
                .build();
        ApikeyCreateOp op = ApikeyCreateOp.builder()
                .parentCode("ak-parent")
                .monthQuota(BigDecimal.ONE)
                .safetyLevel((byte) 10)
                .build();
        when(apikeyRepo.queryByCode("ak-parent")).thenReturn(parent);
        when(apikeyRepo.queryByUniqueKey("ak-parent")).thenReturn(new ApikeyDB());
        when(apikeyRepo.insert(any(ApikeyDB.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createByParentCode(op);

        verify(apikeyRepo, never()).countActivePersonalParents(any());
        verify(apikeyRepo).insert(any(ApikeyDB.class));
    }

    private ApikeyOps.ApplyOp personalApplyOp() {
        return ApikeyOps.ApplyOp.builder()
                .ownerType(PERSON)
                .ownerCode("person-1")
                .ownerName("person")
                .build();
    }
}
