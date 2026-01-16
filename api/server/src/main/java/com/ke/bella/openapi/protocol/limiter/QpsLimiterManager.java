package com.ke.bella.openapi.protocol.limiter;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * QPS 限流管理器
 * 基于分段滑动窗口实现高性能 QPS 限流，天然支持 Redis Cluster
 *
 * 数据结构：
 *   限流 Key: bella-openapi-limiter-qps:{akCode} (Hash，每个 APIKey 独立)
 *     - 1 秒窗口分成 5 个 200ms 段，每段一个字段存储计数
 *     - 固定内存开销，自动过期
 */
@Component
@Slf4j
public class QpsLimiterManager {

    @Autowired
    private LuaScriptExecutor executor;

    @Autowired
    private RedissonClient redisson;

    /**
     * QPS 限流开关，默认开启
	 */
    @Getter
	@Value("${bella.limiter.qps.enabled:true}")
    private boolean enabled;

    /**
     * 默认 QPS 限制值，当 APIKey 未配置时使用
	 */
    @Getter
	@Value("${bella.limiter.qps.default-limit:200}")
    private int defaultLimit;

    private static final String QPS_KEY_FORMAT = "bella-openapi-limiter-qps:%s";
    private static final String QPS_KEY_PREFIX = "bella-openapi-limiter-qps:";

    /**
     * 分段滑动窗口配置
     * 窗口大小 = SEGMENT_SIZE_MS * NUM_SEGMENTS = 1000ms = 1秒
     */
    private static final long SEGMENT_SIZE_MS = 200L;
    private static final int NUM_SEGMENTS = 5;

    /**
     * 检查 QPS 限制（使用 APIKey 配置的限制值，未配置则使用默认值）
     *
     * @param akCode   API Key 编码
     * @param qpsLimit APIKey 配置的 QPS 限制值，可为 null
     * @return 检查结果，包含是否允许、当前 QPS、限制值
     */
    public QpsCheckResult checkLimit(String akCode, Integer qpsLimit) {
        // 限流开关关闭时直接放行
        if (!enabled) {
            return QpsCheckResult.skipped();
        }

        // 处理默认值：null 或 0 使用默认限制，负数表示不限制
        int effectiveLimit = resolveEffectiveLimit(qpsLimit);
        if (effectiveLimit < 0) {
            return QpsCheckResult.skipped();  // 负数表示不限制
        }

        return doCheckLimit(akCode, effectiveLimit);
    }

    /**
     * 解析有效的 QPS 限制值
     *
     * @param qpsLimit APIKey 配置的值
     * @return 有效的限制值（负数表示不限制）
     */
    private int resolveEffectiveLimit(Integer qpsLimit) {
        if (qpsLimit == null || qpsLimit == 0) {
            return defaultLimit;  // 使用默认值
        }
        return qpsLimit;  // 使用配置值
    }

    /**
     * 执行 QPS 限流检查
     */
    private QpsCheckResult doCheckLimit(String akCode, int qpsLimit) {
        String key = String.format(QPS_KEY_FORMAT, akCode);
        long currentTimeMs = System.currentTimeMillis();

        List<Object> keys = Lists.newArrayList(key);
        List<Object> params = new ArrayList<>();
        params.add(qpsLimit);
        params.add(currentTimeMs);
        params.add(SEGMENT_SIZE_MS);
        params.add(NUM_SEGMENTS);

        try {
            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) executor.execute("/qps", ScriptType.limiter, keys, params);

            if (result != null && result.size() >= 2) {
                Long isAllowed = (Long) result.get(0);
                Long currentCount = (Long) result.get(1);

                if (isAllowed == 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("QPS limit exceeded for akCode: {}, limit: {}, current: {}",
                                akCode, qpsLimit, currentCount);
                    }
                    return QpsCheckResult.rejected(currentCount, qpsLimit);
                }

                return QpsCheckResult.allowed(currentCount, qpsLimit);
            }

            log.error("Unexpected result from QPS limiter script: {}", result);
            return QpsCheckResult.skipped();

        } catch (IOException e) {
            log.error("Failed to execute QPS limiter script for akCode: {}, error: {}",
                    akCode, e.getMessage(), e);
            return QpsCheckResult.skipped();
        } catch (Exception e) {
            log.error("Unexpected error in QPS limiter for akCode: {}, error: {}",
                    akCode, e.getMessage(), e);
            return QpsCheckResult.skipped();
        }
    }

    /**
     * 获取当前 QPS（近似值）
     * 第一个窗口（当前段）按已过去时间比例放大，向上取整
     *
     * @param akCode API Key 编码
     * @return 当前 QPS 值
     */
    public Long getCurrentQps(String akCode) {
        String key = String.format(QPS_KEY_FORMAT, akCode);
        long currentTimeMs = System.currentTimeMillis();

        try {
            RMap<String, String> hashMap = redisson.getMap(key);
            Map<String, String> allFields = hashMap.readAllMap();

            if (allFields.isEmpty()) {
                return 0L;
            }

            long currentSegment = currentTimeMs / SEGMENT_SIZE_MS;
            return calculateQpsFromFields(allFields, currentSegment, currentTimeMs);
        } catch (Exception e) {
            log.error("Failed to get current QPS for akCode: {}, error: {}",
                    akCode, e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * 获取 QPS Top N 排行榜（按需查询）
     * 扫描 Redis 中活跃的限流 key，实时计算排行
     *
     * @param topN 返回前 N 名
     * @return 排行榜列表，按 QPS 降序排列
     */
    public List<QpsRankEntry> getTopN(int topN) {
        if (topN <= 0) {
            return Collections.emptyList();
        }

        try {
            long currentTimeMs = System.currentTimeMillis();
            long currentSegment = currentTimeMs / SEGMENT_SIZE_MS;

            // 扫描活跃的限流 key（限制最大扫描数量，防止慢查询）
            String pattern = QPS_KEY_PREFIX + "*";
            Iterable<String> keys = redisson.getKeys().getKeysByPattern(pattern);

            List<QpsRankEntry> entries = new ArrayList<>();
            int scanCount = 0;
            int maxScan = 1000;

            for (String key : keys) {
                if (scanCount++ >= maxScan) {
                    log.warn("getTopN scan limit reached: {}, some keys may be skipped", maxScan);
                    break;
                }

                // 直接读取 Hash，避免 N+1 查询
                RMap<String, String> hashMap = redisson.getMap(key);
                Map<String, String> allFields = hashMap.readAllMap();

                if (!allFields.isEmpty()) {
                    long total = calculateQpsFromFields(allFields, currentSegment);
                    if (total > 0) {
                        String akCode = key.substring(QPS_KEY_PREFIX.length());
                        entries.add(new QpsRankEntry(akCode, total));
                    }
                }
            }

            // 按 QPS 降序排列，取 Top N
            return entries.stream()
                    .sorted((a, b) -> Long.compare(b.getQps(), a.getQps()))
                    .limit(topN)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get QPS top N, error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 从 Hash 字段计算 QPS（内部方法，用于 getTopN，不做时间比例调整）
     */
    private long calculateQpsFromFields(Map<String, String> allFields, long currentSegment) {
        long total = 0;

        for (int i = 0; i < NUM_SEGMENTS; i++) {
            long segmentId = currentSegment - i;
            String countStr = allFields.get(String.valueOf(segmentId));
            if (countStr != null) {
                total += Long.parseLong(countStr);
            }
        }

        return total;
    }

    /**
     * 从 Hash 字段计算 QPS（带时间比例调整）
     * 第一个窗口（当前段）按已过去时间比例放大，向上取整
     * 最小时间阈值：40ms，防止放大倍数过大（最大 5 倍）
     *
     * @param allFields      所有段的计数
     * @param currentSegment 当前段 ID
     * @param currentTimeMs  当前时间戳（毫秒）
     * @return 估算的 QPS 值
     */
    private long calculateQpsFromFields(Map<String, String> allFields, long currentSegment, long currentTimeMs) {
        // 最小时间阈值 40ms，防止放大倍数过大（最大 5 倍）
        long minElapsedMs = SEGMENT_SIZE_MS / 5;
        long total = 0;

        for (int i = 0; i < NUM_SEGMENTS; i++) {
            long segmentId = currentSegment - i;
            String countStr = allFields.get(String.valueOf(segmentId));
            if (countStr != null) {
                long count = Long.parseLong(countStr);
                if (i == 0) {
                    // 第一个窗口（当前段），按已过去时间比例放大，向上取整
                    long segmentStartMs = currentSegment * SEGMENT_SIZE_MS;
                    long elapsedMs = currentTimeMs - segmentStartMs;
                    if (elapsedMs >= minElapsedMs && elapsedMs < SEGMENT_SIZE_MS) {
                        // 预测整个段的请求数 = count * (SEGMENT_SIZE_MS / elapsedMs)
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
