package com.ke.bella.openapi.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
}
