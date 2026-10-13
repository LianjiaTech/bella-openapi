package com.ke.bella.openapi.protocol.live.session;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.limiter.ConcurrentPermitLimiter;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LiveSessionAdmissionServiceTest {
    @Test
    public void leaseReleaseIsExactlyOnce() {
        LiveSessionAdmissionService service = new LiveSessionAdmissionService();
        ConcurrentPermitLimiter permitLimiter = mock(ConcurrentPermitLimiter.class);
        when(permitLimiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn("permit-1", null, "permit-2");
        ReflectionTestUtils.setField(service, "permitLimiter", permitLimiter);
        ReflectionTestUtils.setField(service, "maxConcurrentPerAk", 1);
        ReflectionTestUtils.setField(service, "permitTtlSeconds", 60);

        LiveSessionAdmissionService.Lease lease = service.acquire("ak-1");
        try {
            service.acquire("ak-1");
            fail("expected rate limit");
        } catch (BellaException.RateLimitException expected) {
            lease.close();
            lease.close();
        }

        service.acquire("ak-1").close();
        verify(permitLimiter, times(1)).tryRelease("bella-openapi-live-concurrent:ak-1", "permit-1");
        verify(permitLimiter, times(1)).tryRelease("bella-openapi-live-concurrent:ak-1", "permit-2");
    }
}
