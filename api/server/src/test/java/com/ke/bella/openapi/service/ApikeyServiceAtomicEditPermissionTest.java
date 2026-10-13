package com.ke.bella.openapi.service;

import com.ke.bella.openapi.apikey.AkOperation;
import com.ke.bella.openapi.apikey.ApikeyOps;
import com.ke.bella.openapi.db.repo.ApikeyRepo;
import com.ke.bella.openapi.tables.pojos.ApikeyDB;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(MockitoJUnitRunner.class)
public class ApikeyServiceAtomicEditPermissionTest {

    @InjectMocks
    private ApikeyService service;

    @Mock
    private ApikeyRepo apikeyRepo;

    @Mock
    private AkPermissionChecker akPermissionChecker;

    @Test
    public void renameChecksResourcePermissionBeforeUpdate() {
        ApikeyDB target = new ApikeyDB();
        when(apikeyRepo.queryByUniqueKey("ak-parent")).thenReturn(target);
        ApikeyOps.NameOp op = ApikeyOps.NameOp.builder().code("ak-parent").name("new-name").build();

        service.rename(op);

        verify(akPermissionChecker).check(target, AkOperation.RENAME);
        verify(apikeyRepo).update(op, "ak-parent");
    }

    @Test
    public void bindServiceChecksResourcePermissionBeforeUpdate() {
        ApikeyDB target = new ApikeyDB();
        when(apikeyRepo.queryByUniqueKey("ak-parent")).thenReturn(target);
        ApikeyOps.ServiceOp op = ApikeyOps.ServiceOp.builder().code("ak-parent").serviceId("new-service").build();

        service.bindService(op);

        verify(akPermissionChecker).check(target, AkOperation.BIND_SERVICE);
        verify(apikeyRepo).update(op, "ak-parent");
    }

    @Test
    public void renameReportsMissingApiKeyBeforePermissionCheck() {
        ApikeyOps.NameOp op = ApikeyOps.NameOp.builder().code("missing-ak").name("new-name").build();

        assertThatThrownBy(() -> service.rename(op))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AK不存在");

        verify(akPermissionChecker, never()).check(any(ApikeyDB.class), any(AkOperation.class));
        verify(apikeyRepo, never()).update(op, "missing-ak");
    }
}
