package com.ke.bella.openapi.protocol.limiter.manager;

import com.ke.bella.openapi.protocol.limiter.QpsCheckResult;
import com.ke.bella.openapi.protocol.limiter.QpsRankEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractQpsLimiterManager implements QpsLimiterManager {

    @Getter
    @Value("${bella.limiter.qps.enabled:true}")
    private boolean enabled;

    @Getter
    @Value("${bella.limiter.qps.default-limit:200}")
    private int defaultLimit;

    protected static final String QPS_KEY_FORMAT = "bella-openapi-limiter-qps:%s";
    protected static final String QPS_KEY_PREFIX = "bella-openapi-limiter-qps:";
    protected static final String LUA_SCRIPT_PATH = "lua/limiter/qps.lua";
    protected static final long SEGMENT_SIZE_MS = 200L;
    protected static final int NUM_SEGMENTS = 5;

    private final ConcurrentHashMap<String, String> scriptShaCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            loadScript();
        } catch (Exception e) {
            log.warn("Failed to preload QPS lua script, will retry on first use: {}", e.getMessage());
        }
    }

    protected String loadScript() throws IOException {
        ClassPathResource resource = new ClassPathResource(LUA_SCRIPT_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            String scriptContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            String sha = doScriptLoad(scriptContent);
            scriptShaCache.put(LUA_SCRIPT_PATH, sha);
            return sha;
        }
    }

    protected String getScriptSha() throws IOException {
        String sha = scriptShaCache.get(LUA_SCRIPT_PATH);
        if(sha == null) {
            sha = loadScript();
        }
        return sha;
    }

    @Override
    public QpsCheckResult checkLimit(String akCode, Integer qpsLimit) {
        if(!enabled) {
            return QpsCheckResult.skipped();
        }
        int effectiveLimit = resolveEffectiveLimit(qpsLimit);
        if(effectiveLimit < 0) {
            return QpsCheckResult.skipped();
        }
        return doCheckLimit(akCode, effectiveLimit);
    }

    private int resolveEffectiveLimit(Integer qpsLimit) {
        if(qpsLimit == null || qpsLimit == 0) {
            return defaultLimit;
        }
        return qpsLimit;
    }

    private QpsCheckResult doCheckLimit(String akCode, int qpsLimit) {
        String key = String.format(QPS_KEY_FORMAT, akCode);
        long currentTimeMs = System.currentTimeMillis();
        try {
            String sha = getScriptSha();
            List<Object> result = execEvalsha(sha, key, qpsLimit, currentTimeMs);
            if(result != null && result.size() >= 2) {
                long isAllowed = (Long) result.get(0);
                long currentCount = (Long) result.get(1);
                if(isAllowed == 0) {
                    if(log.isDebugEnabled()) {
                        log.debug("QPS limit exceeded for akCode: {}, limit: {}, current: {}", akCode, qpsLimit, currentCount);
                    }
                    return QpsCheckResult.rejected(currentCount, qpsLimit);
                }
                return QpsCheckResult.allowed(currentCount, qpsLimit);
            }
            log.error("Unexpected result from QPS limiter script: {}", result);
            return QpsCheckResult.skipped();
        } catch (IOException e) {
            log.error("Failed to load QPS lua script for akCode: {}, error: {}", akCode, e.getMessage(), e);
            return QpsCheckResult.skipped();
        } catch (Exception e) {
            log.error("Unexpected error in QPS limiter for akCode: {}, error: {}", akCode, e.getMessage(), e);
            return QpsCheckResult.skipped();
        }
    }

    @Override
    public Long getCurrentQps(String akCode) {
        String key = String.format(QPS_KEY_FORMAT, akCode);
        long currentTimeMs = System.currentTimeMillis();
        long currentSegment = currentTimeMs / SEGMENT_SIZE_MS;
        try {
            Map<String, String> allFields = doReadAllSegments(key);
            if(allFields.isEmpty()) {
                return 0L;
            }
            return calculateQps(allFields, currentSegment, currentTimeMs);
        } catch (Exception e) {
            log.error("Failed to get current QPS for akCode: {}, error: {}", akCode, e.getMessage(), e);
            return 0L;
        }
    }

    @Override
    public List<QpsRankEntry> getTopN(int topN) {
        if(topN <= 0) {
            return Collections.emptyList();
        }
        try {
            long currentTimeMs = System.currentTimeMillis();
            long currentSegment = currentTimeMs / SEGMENT_SIZE_MS;
            List<String> keys = doScanKeys(1000);

            return keys.stream()
                    .map(key -> {
                        Map<String, String> allFields = doReadAllSegments(key);
                        long total = calculateSimpleQps(allFields, currentSegment);
                        String akCode = key.substring(QPS_KEY_PREFIX.length());
                        return new QpsRankEntry(akCode, total);
                    })
                    .filter(e -> e.getQps() > 0)
                    .sorted((a, b) -> Long.compare(b.getQps(), a.getQps()))
                    .limit(topN)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get QPS top N, error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private long calculateQps(Map<String, String> allFields, long currentSegment, long currentTimeMs) {
        // 最小时间阈值 40ms，防止放大倍数过大（最大 5 倍）
        long minElapsedMs = SEGMENT_SIZE_MS / 5;
        long total = 0;
        for (int i = 0; i < NUM_SEGMENTS; i++) {
            long segmentId = currentSegment - i;
            String countStr = allFields.get(String.valueOf(segmentId));
            if(countStr != null) {
                long count = Long.parseLong(countStr);
                if(i == 0) {
                    // 第一个窗口（当前段），按已过去时间比例放大，向上取整
                    long segmentStartMs = currentSegment * SEGMENT_SIZE_MS;
                    long elapsedMs = currentTimeMs - segmentStartMs;
                    if(elapsedMs >= minElapsedMs && elapsedMs < SEGMENT_SIZE_MS) {
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

    private long calculateSimpleQps(Map<String, String> values, long currentSegment) {
        long total = 0;
        for (int i = 0; i < NUM_SEGMENTS; i++) {
            String countStr = values.get(String.valueOf(currentSegment - i));
            if(countStr != null) {
                total += Long.parseLong(countStr);
            }
        }
        return total;
    }

    /** 将 Lua 脚本注册到 Redis，返回 SHA */
    protected abstract String doScriptLoad(String scriptContent) throws IOException;

    /** 执行 QPS 限流 Lua 脚本，含 NOSCRIPT 重试逻辑 */
    protected abstract List<Object> execEvalsha(String sha, String key, int qpsLimit, long currentTimeMs) throws Exception;

    /** 读取 key 下所有 Hash 段数据，返回 field -> count 的 Map */
    protected abstract Map<String, String> doReadAllSegments(String key);

    /** 扫描所有活跃的 QPS 限流 key，最多返回 maxScan 个 */
    protected abstract List<String> doScanKeys(int maxScan);
}
