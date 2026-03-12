package com.ke.bella.openapi.limiter;

import com.ke.bella.openapi.protocol.limiter.QpsCheckResult;
import com.ke.bella.openapi.protocol.limiter.QpsRankEntry;
import com.ke.bella.openapi.protocol.limiter.manager.AbstractQpsLimiterManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class AbstractQpsLimiterManagerTest {

    private TestQpsLimiterManager manager;

    @Before
    public void setUp() {
        manager = new TestQpsLimiterManager();
        manager.setEnabled(true);
        manager.setDefaultLimit(200);
    }

    /**
     * 测试：QPS 限流开关关闭时应跳过检查
     */
    @Test
    public void testCheckLimit_disabled_shouldSkip() {
        manager.setEnabled(false);

        QpsCheckResult result = manager.checkLimit("ak_test", 100);

        assertTrue(result.isAllowed());
        assertEquals(-1, result.getLimit());
    }

    /**
     * 测试：使用默认限制值（qpsLimit 为 null）
     */
    @Test
    public void testCheckLimit_nullLimit_shouldUseDefault() {
        manager.mockLuaResult(Arrays.asList(1L, 150L)); // allowed, currentQps=150

        QpsCheckResult result = manager.checkLimit("ak_test", null);

        assertTrue(result.isAllowed());
        assertEquals(200, result.getLimit()); // 使用默认值
        assertEquals(150, result.getCurrentQps());
    }

    /**
     * 测试：QPS 未超限时应允许
     */
    @Test
    public void testCheckLimit_underLimit_shouldAllow() {
        manager.mockLuaResult(Arrays.asList(1L, 80L)); // allowed, currentQps=80

        QpsCheckResult result = manager.checkLimit("ak_test", 100);

        assertTrue(result.isAllowed());
        assertEquals(100, result.getLimit());
        assertEquals(80, result.getCurrentQps());
    }

    /**
     * 测试：QPS 超限时应拒绝
     */
    @Test
    public void testCheckLimit_overLimit_shouldReject() {
        manager.mockLuaResult(Arrays.asList(0L, 150L)); // rejected, currentQps=150

        QpsCheckResult result = manager.checkLimit("ak_test", 100);

        assertFalse(result.isAllowed());
        assertEquals(100, result.getLimit());
        assertEquals(150, result.getCurrentQps());
    }

    /**
     * 测试：getCurrentQps - 无数据时返回 0
     */
    @Test
    public void testGetCurrentQps_noData_shouldReturnZero() {
        manager.mockSegmentData(Collections.emptyMap());

        Long qps = manager.getCurrentQps("ak_test");

        assertEquals(Long.valueOf(0), qps);
    }

    /**
     * 测试：getCurrentQps - 当前段预测逻辑（过去 50ms）
     */
    @Test
    public void testGetCurrentQps_currentSegment_shouldPredict() {
        long currentTimeMs = 1710140401050L; // 某个时间点的 50ms
        long currentSegment = currentTimeMs / 200; // = 8550702005

        Map<String, String> data = new HashMap<>();
        data.put(String.valueOf(currentSegment), "10");     // 当前段 50ms 内 10 个请求
        data.put(String.valueOf(currentSegment - 1), "52"); // 上一段
        data.put(String.valueOf(currentSegment - 2), "48");
        data.put(String.valueOf(currentSegment - 3), "50");
        data.put(String.valueOf(currentSegment - 4), "43");

        manager.mockSegmentData(data);
        manager.setCurrentTimeMs(currentTimeMs);

        Long qps = manager.getCurrentQps("ak_test");

        // 预期：当前段预测 = ceil(10 * 200 / 50) = 40
        // 总 QPS = 40 + 52 + 48 + 50 + 43 = 233
        assertEquals(Long.valueOf(233), qps);
    }

    /**
     * 测试：getTopN - 无数据时返回空列表
     */
    @Test
    public void testGetTopN_noKeys_shouldReturnEmpty() {
        manager.mockScanKeys(Collections.emptyList());

        List<QpsRankEntry> result = manager.getTopN(10);

        assertTrue(result.isEmpty());
    }

    /**
     * 测试：getTopN - 正常排序
     */
    @Test
    public void testGetTopN_normalSort_shouldReturnTopN() {
        long currentSegment = System.currentTimeMillis() / 200;

        // Mock 扫描结果
        List<String> keys = Arrays.asList(
                "bella-openapi-limiter-qps:ak_user1",
                "bella-openapi-limiter-qps:ak_user2",
                "bella-openapi-limiter-qps:ak_user3"
        );
        manager.mockScanKeys(keys);

        // Mock 每个 key 的数据
        Map<String, String> data1 = createSegmentData(currentSegment, 15, 60, 55, 58, 52); // 总 240
        Map<String, String> data2 = createSegmentData(currentSegment, 8, 52, 48, 50, 43);  // 总 201
        Map<String, String> data3 = createSegmentData(currentSegment, 20, 80, 75, 78, 70); // 总 323

        manager.mockSegmentDataForKey("bella-openapi-limiter-qps:ak_user1", data1);
        manager.mockSegmentDataForKey("bella-openapi-limiter-qps:ak_user2", data2);
        manager.mockSegmentDataForKey("bella-openapi-limiter-qps:ak_user3", data3);

        List<QpsRankEntry> result = manager.getTopN(3);

        assertEquals(3, result.size());
        // 降序排列
        assertEquals("ak_user3", result.get(0).getAkCode());
        assertEquals(Long.valueOf(323), result.get(0).getQps());
        assertEquals("ak_user1", result.get(1).getAkCode());
        assertEquals(Long.valueOf(240), result.get(1).getQps());
        assertEquals("ak_user2", result.get(2).getAkCode());
        assertEquals(Long.valueOf(201), result.get(2).getQps());
    }

    // ========== Helper Methods ==========

    private Map<String, String> createSegmentData(long currentSegment, long... counts) {
        Map<String, String> data = new HashMap<>();
        for (int i = 0; i < counts.length && i < 5; i++) {
            data.put(String.valueOf(currentSegment - i), String.valueOf(counts[i]));
        }
        return data;
    }

    // ========== Test Implementation ==========

    /**
     * 测试用的具体实现
     */
    static class TestQpsLimiterManager extends AbstractQpsLimiterManager {
        private List<Object> mockLuaResult;
        private Exception mockLuaException;
        private Map<String, String> mockSegmentData = new HashMap<>();
        private Exception mockSegmentException;
        private List<String> mockKeys = new ArrayList<>();
        private Map<String, Map<String, String>> keySpecificData = new HashMap<>();
        private long currentTimeMs = System.currentTimeMillis();

        public void setEnabled(boolean enabled) {
            try {
                java.lang.reflect.Field field = AbstractQpsLimiterManager.class.getDeclaredField("enabled");
                field.setAccessible(true);
                field.set(this, enabled);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void setDefaultLimit(int defaultLimit) {
            try {
                java.lang.reflect.Field field = AbstractQpsLimiterManager.class.getDeclaredField("defaultLimit");
                field.setAccessible(true);
                field.set(this, defaultLimit);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void setCurrentTimeMs(long currentTimeMs) {
            this.currentTimeMs = currentTimeMs;
        }

        public void mockLuaResult(List<Object> result) {
            this.mockLuaResult = result;
            this.mockLuaException = null;
        }

        public void mockLuaException(Exception exception) {
            this.mockLuaException = exception;
            this.mockLuaResult = null;
        }

        public void mockSegmentData(Map<String, String> data) {
            this.mockSegmentData = data;
            this.mockSegmentException = null;
        }

        public void mockSegmentException(Exception exception) {
            this.mockSegmentException = exception;
        }

        public void mockScanKeys(List<String> keys) {
            this.mockKeys = keys;
        }

        public void mockSegmentDataForKey(String key, Map<String, String> data) {
            this.keySpecificData.put(key, data);
        }

        @Override
        protected String doScriptLoad(String scriptContent) {
            return "mock_sha";
        }

        @Override
        protected List<Object> execEvalsha(String sha, String key, int qpsLimit, long currentTimeMs) throws Exception {
            if (mockLuaException != null) {
                throw mockLuaException;
            }
            return mockLuaResult;
        }

        @Override
        protected Map<String, String> doReadAllSegments(String key) {
            if (mockSegmentException != null) {
                throw new RuntimeException(mockSegmentException);
            }
            // 如果有针对特定 key 的数据，使用它
            if (keySpecificData.containsKey(key)) {
                return keySpecificData.get(key);
            }
            return mockSegmentData;
        }

        @Override
        protected List<String> doScanKeys(int maxScan) {
            return mockKeys;
        }

        @Override
        public Long getCurrentQps(String akCode) {
            // 重写以使用 mock 的时间
            String key = String.format(QPS_KEY_FORMAT, akCode);
            long currentSegment = currentTimeMs / SEGMENT_SIZE_MS;
            try {
                Map<String, String> allFields = doReadAllSegments(key);
                if (allFields.isEmpty()) {
                    return 0L;
                }
                return calculateQpsWithTime(allFields, currentSegment, currentTimeMs);
            } catch (Exception e) {
                return 0L;
            }
        }

        // 暴露私有方法用于测试
        private long calculateQpsWithTime(Map<String, String> allFields, long currentSegment, long currentTimeMs) {
            long minElapsedMs = SEGMENT_SIZE_MS / 5;
            long total = 0;
            for (int i = 0; i < NUM_SEGMENTS; i++) {
                long segmentId = currentSegment - i;
                String countStr = allFields.get(String.valueOf(segmentId));
                if (countStr != null) {
                    long count = Long.parseLong(countStr);
                    if (i == 0) {
                        long segmentStartMs = currentSegment * SEGMENT_SIZE_MS;
                        long elapsedMs = currentTimeMs - segmentStartMs;
                        if (elapsedMs >= minElapsedMs && elapsedMs < SEGMENT_SIZE_MS) {
                            total += (long) Math.ceil((double) count * SEGMENT_SIZE_MS / elapsedMs);
                        } else {
                            total += count;
                        }
                    } else {
                        total += count;
                    }
                }
            }
            return total;
        }
    }
}
