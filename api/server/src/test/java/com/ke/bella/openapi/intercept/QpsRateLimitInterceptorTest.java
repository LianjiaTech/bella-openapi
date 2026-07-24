package com.ke.bella.openapi.intercept;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.limiter.QpsCheckResult;
import com.ke.bella.openapi.protocol.limiter.QpsLimiterManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class QpsRateLimitInterceptorTest {
    private static final String AK_CODE = "ak-test-1010";

    @Mock
    private QpsLimiterManager qpsLimiterManager;

    private QpsRateLimitInterceptor interceptor;
    private SimpleMeterRegistry meterRegistry;

    @Before
    public void setUp() {
        interceptor = new QpsRateLimitInterceptor();
        meterRegistry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(interceptor, "qpsLimiterManager", qpsLimiterManager);
        ReflectionTestUtils.setField(interceptor, "meterRegistry", meterRegistry);
    }

    @After
    public void tearDown() {
        EndpointContext.clearAll();
        meterRegistry.close();
    }

    @Test
    public void preHandleRecordsAkMetricBeforeAllowedLimitCheck() {
        EndpointContext.setApikey(apikey(100));
        when(qpsLimiterManager.checkLimit(eq(AK_CODE), eq(100)))
                .thenAnswer(invocation -> {
                    assertCounter(1.0);
                    return QpsCheckResult.allowed(1, 100);
                });

        boolean allowed = interceptor.preHandle(request(), new MockHttpServletResponse(), null);

        assertEquals(true, allowed);
        assertCounter(1.0);
        verify(qpsLimiterManager).checkLimit(eq(AK_CODE), eq(100));
    }

    @Test
    public void preHandleRecordsAkMetricWhenLimitRejected() {
        EndpointContext.setApikey(apikey(1));
        when(qpsLimiterManager.checkLimit(eq(AK_CODE), eq(1)))
                .thenReturn(QpsCheckResult.rejected(2, 1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            interceptor.preHandle(request(), response, null);
            fail("Expected RateLimitException");
        } catch (BellaException.RateLimitException ignored) {
        }

        assertCounter(1.0);
        assertEquals("1", response.getHeader("Retry-After"));
    }

    @Test
    public void preHandleRecordsAkMetricWhenLimitSkipped() {
        EndpointContext.setApikey(apikey(-1));
        when(qpsLimiterManager.checkLimit(eq(AK_CODE), eq(-1)))
                .thenReturn(QpsCheckResult.skipped());

        boolean allowed = interceptor.preHandle(request(), new MockHttpServletResponse(), null);

        assertEquals(true, allowed);
        assertCounter(1.0);
    }

    @Test
    public void preHandleDoesNotRecordAkMetricForAsyncDispatch() {
        EndpointContext.setApikey(apikey(100));
        MockHttpServletRequest request = request();
        request.setAttribute(ASYNC_REQUEST_MARKER, Boolean.TRUE);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), null);

        assertEquals(true, allowed);
        assertEquals(0, akRequestMeterCount());
        verify(qpsLimiterManager, never()).checkLimit(eq(AK_CODE), eq(100));
    }

    @Test
    public void preHandleDoesNotRecordAkMetricForRealtimeTtsWebSocketUpgrade() {
        EndpointContext.setApikey(apikey(100));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/audio/tts/stream");
        request.addHeader("Upgrade", "websocket");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), null);

        assertEquals(true, allowed);
        assertEquals(0, akRequestMeterCount());
        verify(qpsLimiterManager, never()).checkLimit(eq(AK_CODE), eq(100));
    }

    private ApikeyInfo apikey(Integer qpsLimit) {
        return ApikeyInfo.builder()
                .apikey("real-secret-value")
                .code(AK_CODE)
                .qpsLimit(qpsLimit)
                .build();
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/v1/chat/completions");
    }

    private void assertCounter(double expected) {
        Counter counter = meterRegistry.find("bella_ak_requests")
                .tag("ak_code", AK_CODE)
                .counter();
        assertNotNull(counter);
        assertEquals(expected, counter.count(), 0.0);
        assertEquals(1, counter.getId().getTags().size());
        assertEquals("ak_code", counter.getId().getTags().get(0).getKey());
        assertEquals(AK_CODE, counter.getId().getTags().get(0).getValue());
    }

    private long akRequestMeterCount() {
        return meterRegistry.getMeters().stream()
                .map(Meter::getId)
                .filter(id -> "bella_ak_requests".equals(id.getName()))
                .count();
    }
}
