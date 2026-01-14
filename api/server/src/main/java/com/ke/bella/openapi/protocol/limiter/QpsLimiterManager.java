package com.ke.bella.openapi.protocol.limiter;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
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
 *     - 1 秒窗口分成 10 个 100ms 段，每段一个字段存储计数
 *     - 使用时间权重计算精确用量，固定内存开销
 *   排行榜 Key: bella-openapi-qps-ranking (ZSET，全局)
 *
 * 注意：排行榜数据为近似值，记录的是最后一次请求时的瞬时 QPS，
 *      仅用于监控参考，非精确实时数据。
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
	@Value("${bella.limiter.qps.default-limit:100}")
    private int defaultLimit;

    private static final String QPS_KEY_FORMAT = "bella-openapi-limiter-qps:%s";
    private static final String RANKING_KEY = "bella-openapi-qps-ranking";
    private static final long RANKING_EXPIRE_SECONDS = 60L;

    /**
     * 分段滑动窗口配置
     * 窗口大小 = SEGMENT_SIZE_MS * NUM_SEGMENTS = 1000ms = 1秒
     */
    private static final long SEGMENT_SIZE_MS = 100L;
    private static final int NUM_SEGMENTS = 10;

    /**
     * 检查 QPS 限制（使用 APIKey 配置的限制值，未配置则使用默认值）
     *
     * @param akCode   API Key 编码
     * @param qpsLimit APIKey 配置的 QPS 限制值，可为 null
     * @return true=允许通过, false=拒绝（超限）
     */
    public boolean checkLimit(String akCode, Integer qpsLimit) {
        // 限流开关关闭时直接放行
        if (!enabled) {
            return true;
        }

        // 处理默认值：null 或 0 使用默认限制，负数表示不限制
        int effectiveLimit = resolveEffectiveLimit(qpsLimit);
        if (effectiveLimit < 0) {
            return true;  // 负数表示不限制
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
    private boolean doCheckLimit(String akCode, int qpsLimit) {
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

                // 异步更新排行榜（近似值，用于监控）
                updateRankingAsync(akCode, currentCount);

                if (isAllowed == 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("QPS limit exceeded for akCode: {}, limit: {}, current: {}",
                                akCode, qpsLimit, currentCount);
                    }
                    return false;
                }

                return true;
            }

            log.error("Unexpected result from QPS limiter script: {}", result);
            return true;

        } catch (IOException e) {
            log.error("Failed to execute QPS limiter script for akCode: {}, error: {}",
                    akCode, e.getMessage(), e);
            return true;
        } catch (Exception e) {
            log.error("Unexpected error in QPS limiter for akCode: {}, error: {}",
                    akCode, e.getMessage(), e);
            return true;
        }
    }

    /**
     * 异步更新排行榜
     */
    private void updateRankingAsync(String akCode, Long currentCount) {
        try {
            RScoredSortedSet<String> rankingSet = redisson.getScoredSortedSet(RANKING_KEY);
            rankingSet.addAsync(currentCount.doubleValue(), akCode);
            rankingSet.expireAsync(Duration.ofSeconds(RANKING_EXPIRE_SECONDS));
        } catch (Exception e) {
            // 排行榜更新失败不影响限流功能
            log.debug("Failed to update QPS ranking for akCode: {}, error: {}", akCode, e.getMessage());
        }
    }

    /**
     * 获取当前 QPS（加权近似值）
     *
     * @param akCode API Key 编码
     * @return 当前 QPS 值
     */
    public Long getCurrentQps(String akCode) {
        String key = String.format(QPS_KEY_FORMAT, akCode);
        long currentTimeMs = System.currentTimeMillis();
        long windowSizeMs = SEGMENT_SIZE_MS * NUM_SEGMENTS;
        long windowStartMs = currentTimeMs - windowSizeMs;

        try {
            RMap<String, String> hashMap = redisson.getMap(key);
            Map<String, String> allFields = hashMap.readAllMap();

            if (allFields.isEmpty()) {
                return 0L;
            }

            long currentSegment = currentTimeMs / SEGMENT_SIZE_MS;
            double total = 0;

            for (int i = 0; i < NUM_SEGMENTS; i++) {
                long segmentId = currentSegment - i;
                String countStr = allFields.get(String.valueOf(segmentId));
                if (countStr != null) {
                    long count = Long.parseLong(countStr);
                    // 计算该段在窗口内的有效比例（权重）
                    long segmentStartMs = segmentId * SEGMENT_SIZE_MS;
                    long segmentEndMs = segmentStartMs + SEGMENT_SIZE_MS;
                    long effectiveStart = Math.max(segmentStartMs, windowStartMs);
                    long effectiveEnd = Math.min(segmentEndMs, currentTimeMs);
                    double weight = (double) (effectiveEnd - effectiveStart) / SEGMENT_SIZE_MS;
                    if (weight > 0) {
                        total += count * weight;
                    }
                }
            }

            return (long) total;
        } catch (Exception e) {
            log.error("Failed to get current QPS for akCode: {}, error: {}",
                    akCode, e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * 获取 QPS Top N 排行榜
     * 注意：返回的是近似值，记录的是各 APIKey 最后一次请求时的瞬时 QPS
     *
     * @param topN 返回前 N 名
     * @return 排行榜列表，按 QPS 降序排列
     */
    public List<QpsRankEntry> getTopN(int topN) {
        if (topN <= 0) {
            return Collections.emptyList();
        }

        try {
            RScoredSortedSet<String> rankingSet = redisson.getScoredSortedSet(RANKING_KEY);
            Collection<ScoredEntry<String>> entries = rankingSet.entryRangeReversed(0, topN - 1);

            return entries.stream()
                    .map(entry -> new QpsRankEntry(entry.getValue(), entry.getScore().longValue()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get QPS top N, error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

}
