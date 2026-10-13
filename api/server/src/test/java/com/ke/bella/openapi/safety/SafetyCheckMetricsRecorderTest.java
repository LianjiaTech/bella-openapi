package com.ke.bella.openapi.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;

import com.ke.bella.openapi.common.exception.BellaException;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class SafetyCheckMetricsRecorderTest {
    private SimpleMeterRegistry registry;

    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        new SafetyCheckMetricsRecorder(registry);
    }

    @Test
    public void syncDelegatorRecordsSuccessBlockedAndErrorLatency() {
        SafetyCheckRequest.Chat request = request("input", true);

        delegator((req, mock) -> null, SafetyCheckMode.sync).safetyCheck(request, false);
        try {
            delegator((req, mock) -> {
                throw new BellaException.SafetyCheckException("blocked");
            }, SafetyCheckMode.sync).safetyCheck(request("output", false), false);
        } catch (BellaException.SafetyCheckException ignored) {
        }
        delegator((req, mock) -> {
            throw new RuntimeException("safety service unavailable");
        }, SafetyCheckMode.sync).safetyCheck(request, false);

        assertSummaryCount(SafetyCheckMetricsRecorder.SAFETY_CHECK_LATENCY_METRIC, 1,
                "type", "input", "status", "success", "mode", "sync");
        assertSummaryCount(SafetyCheckMetricsRecorder.SAFETY_CHECK_LATENCY_METRIC, 1,
                "type", "output", "status", "blocked", "mode", "sync");
        assertSummaryCount(SafetyCheckMetricsRecorder.SAFETY_CHECK_LATENCY_METRIC, 1,
                "type", "input", "status", "error", "mode", "sync");
    }

    @Test
    public void asyncDelegatorRecordsAsyncModeLatency() throws InterruptedException {
        CountDownLatch checked = new CountDownLatch(1);
        SafetyCheckDelegator<SafetyCheckRequest.Chat> delegator = delegator((req, mock) -> {
            checked.countDown();
            return null;
        }, SafetyCheckMode.async);

        delegator.safetyCheck(request("input", true), false);

        checked.await(3, TimeUnit.SECONDS);
        assertSummaryCountEventually(SafetyCheckMetricsRecorder.SAFETY_CHECK_LATENCY_METRIC, 1,
                "type", "input", "status", "success", "mode", "async");
    }

    @Test
    public void asyncDelegatorLetsServicePrepareRequestBeforeSubmission() throws InterruptedException {
        CountDownLatch checked = new CountDownLatch(1);
        SafetyCheckRequest.Chat original = request("input", true);
        Object downstreamPayload = new Object();
        ISafetyCheckService<SafetyCheckRequest.Chat> service = new ISafetyCheckService<SafetyCheckRequest.Chat>() {
            @Override
            public Supplier<Object> prepareAsyncCheck(SafetyCheckRequest.Chat request, boolean isMock) {
                assertSame(original, request);
                return () -> {
                    assertNotNull(downstreamPayload);
                    checked.countDown();
                    return null;
                };
            }

            @Override
            public Object safetyCheck(SafetyCheckRequest.Chat request, boolean isMock) {
                throw new AssertionError("async path should execute the prepared check");
            }
        };

        delegator(service, SafetyCheckMode.async).safetyCheck(original, false);

        assertEquals(true, checked.await(3, TimeUnit.SECONDS));
    }

    @Test
    public void asyncDelegatorUsesDetachedCheckContextAfterPreparation() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        Object riskData = new Object();
        SafetyCheckRequest.Chat original = request("input", true);
        ISafetyCheckService<SafetyCheckRequest.Chat> service = new ISafetyCheckService<SafetyCheckRequest.Chat>() {
            @Override
            public Supplier<Object> prepareAsyncCheck(SafetyCheckRequest.Chat request, boolean isMock) {
                return () -> {
                    await(release);
                    return riskData;
                };
            }

            @Override
            public Object safetyCheck(SafetyCheckRequest.Chat request, boolean isMock) {
                throw new AssertionError("async path should execute the prepared check");
            }
        };
        SafetyCheckDelegator<SafetyCheckRequest.Chat> delegator = delegator(service, SafetyCheckMode.async);

        delegator.safetyCheck(original, false);
        original.setRequest(false);
        original.setType("output");
        release.countDown();

        assertSummaryCountEventually(SafetyCheckMetricsRecorder.SAFETY_CHECK_LATENCY_METRIC, 1,
                "type", "input", "status", "success", "mode", "async");
        assertSame(riskData, delegator.getRequestRiskData());
        assertNull(delegator.getResponseRiskData());
    }

    @Test
    public void skipDelegatorDoesNotPrepareOrInvokeService() {
        final int[] calls = new int[2];
        ISafetyCheckService<SafetyCheckRequest.Chat> service = new ISafetyCheckService<SafetyCheckRequest.Chat>() {
            @Override
            public Supplier<Object> prepareAsyncCheck(SafetyCheckRequest.Chat request, boolean isMock) {
                calls[0]++;
                return () -> null;
            }

            @Override
            public Object safetyCheck(SafetyCheckRequest.Chat request, boolean isMock) {
                calls[1]++;
                return null;
            }
        };

        delegator(service, SafetyCheckMode.skip).safetyCheck(request("input", true), false);

        assertEquals(0, calls[0]);
        assertEquals(0, calls[1]);
    }

    @Test
    public void streamCheckerRecordsLatencyForTriggeredChecks() {
        StreamSafetyChecker checker = new StreamSafetyChecker((request, mock) -> null, false);

        checker.check(false, repeat("a", 100), () -> request("output", false));
        checker.check(true, repeat("a", 100), () -> request("output", false));

        assertSummaryCount(SafetyCheckMetricsRecorder.SAFETY_CHECK_STREAM_LATENCY_METRIC, 1, "done", "false");
        assertSummaryCount(SafetyCheckMetricsRecorder.SAFETY_CHECK_STREAM_LATENCY_METRIC, 1, "done", "true");
    }

    private SafetyCheckDelegator<SafetyCheckRequest.Chat> delegator(
            ISafetyCheckService<SafetyCheckRequest.Chat> service, SafetyCheckMode mode) {
        return new SafetyCheckDelegator<>(service, mode, new LinkedQueueSafetyResultStorage());
    }

    private SafetyCheckRequest.Chat request(String type, boolean isRequest) {
        return SafetyCheckRequest.Chat.builder()
                .requestId("request-id")
                .type(type)
                .isRequest(isRequest)
                .build();
    }

    private void assertSummaryCount(String name, long count, String... tags) {
        DistributionSummary summary = registry.find(name).tags(tags).summary();
        assertNotNull(summary);
        assertEquals(count, summary.count());
    }

    private void assertSummaryCountEventually(String name, long count, String... tags) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(3);
        while (System.currentTimeMillis() < deadline) {
            DistributionSummary summary = registry.find(name).tags(tags).summary();
            if(summary != null && summary.count() == count) {
                return;
            }
            Thread.sleep(20);
        }
        assertSummaryCount(name, count, tags);
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
