package com.ke.bella.openapi.protocol.limiter;

import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointProcessData;

import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import com.ke.bella.openapi.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.api.RKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LimiterManager {
    @Autowired
    private LuaScriptExecutor executor;

    @Autowired
    private RedissonClient redisson;

    private static final String RPM_KEY_FORMAT = "bella-openapi-limiter-rpm:%s:%s";
    private static final String RPM_COUNT_KEY_FORMAT = "bella-openapi-limiter-rpm-count:%s:%s";
    private static final String CONCURRENT_KEY_FORMAT = "bella-openapi-limiter-concurrent:%s:%s";
    private static final String GLOBAL_RPM_KEY_FORMAT = "bella-openapi-limiter-rpm:global:%s";
    private static final String GLOBAL_RPM_COUNT_KEY_FORMAT = "bella-openapi-limiter-rpm-count:global:%s";
    private static final String QPS_LIMIT_KEY_FORMAT = "bella-openapi-limiter-qps-limit:%s";

    @Value("${bella.openapi.qps.default:100}")
    private Integer defaultQps;

    public void record(EndpointProcessData processData) {
        if(processData.getChannelCode() == null) {
            return;
        }
        String entityCode = processData.getModel() != null ? processData.getModel() : processData.getEndpoint();
        String akCode = processData.getAkCode();
        String requestId = processData.getRequestId();
        if (entityCode == null || akCode == null) {
            return;
        }
        long currentTimestamp = DateTimeUtils.getCurrentSeconds();
        if(requestId != null) {
            // RPM记录
            incrementRequestCountPerMinute(akCode, entityCode, requestId, currentTimestamp);
            // Global RPM记录（按AK聚合）
            incrementGlobalRequestCountPerMinute(akCode, requestId, currentTimestamp);
        }

        // 减少并发请求计数
        decrementConcurrentCount(akCode, entityCode);
    }

    public void incrementRequestCountPerMinute(String akCode, String entityCode, String requestId, long currentTimestamp) {
        String rpmKey = String.format(RPM_KEY_FORMAT, entityCode, akCode);
        String countKey = String.format(RPM_COUNT_KEY_FORMAT, entityCode, akCode);
        executeRpmScript(rpmKey, countKey, requestId, currentTimestamp);
    }


    public Long getRequestCountPerMinute(String akCode, String entityCode) {
        String countKey = String.format(RPM_COUNT_KEY_FORMAT, entityCode, akCode);
        return getCountFromRedis(countKey);
    }

    public void incrementGlobalRequestCountPerMinute(String akCode, String requestId, long currentTimestamp) {
        String rpmKey = String.format(GLOBAL_RPM_KEY_FORMAT, akCode);
        String countKey = String.format(GLOBAL_RPM_COUNT_KEY_FORMAT, akCode);
        executeRpmScript(rpmKey, countKey, requestId, currentTimestamp);
    }

    public Long getGlobalRequestCountPerMinute(String akCode) {
        String countKey = String.format(GLOBAL_RPM_COUNT_KEY_FORMAT, akCode);
        return getCountFromRedis(countKey);
    }

    @Cached(name = "qpsLimit:", key = "#akCode", expire = 1, timeUnit = TimeUnit.SECONDS, cacheType = CacheType.LOCAL)
    public Long getGlobalQpsLimit(String akCode) {
        String key = String.format(QPS_LIMIT_KEY_FORMAT, akCode);
        Object val = redisson.getBucket(key).get();
        if (val == null) {
            return defaultQps != null ? defaultQps.longValue() : 100L;
        }
        return Long.parseLong(val.toString());
    }

    public void updateGlobalQpsLimit(String akCode, Long qps) {
        String key = String.format(QPS_LIMIT_KEY_FORMAT, akCode);
        redisson.getBucket(key).set(qps);
    }

    public boolean isGlobalQpsExceeded(String akCode) {
        Long rpm = getGlobalRequestCountPerMinute(akCode);
        Long limitQps = getGlobalQpsLimit(akCode);
        long limitRpm = (limitQps != null ? limitQps : 100L) * 60L;
        return rpm != null && rpm >= limitRpm;
    }

    public List<Map<String, Object>> listTopAkByQps(int topN) {
        List<Map<String, Object>> items = new ArrayList<>();

        try {
            String pattern = String.format(GLOBAL_RPM_COUNT_KEY_FORMAT, "*");
            RKeys rKeys = redisson.getKeys();
            Iterable<String> keys = rKeys.getKeysByPattern(pattern);

            for (String key : keys) {
                try {
                    String akCode = key.substring(key.lastIndexOf(':') + 1);
                    Long rpm = getGlobalRequestCountPerMinute(akCode);
                    Long limitQps = getGlobalQpsLimit(akCode);
                    double qps = (rpm != null ? rpm : 0L) / 60.0;

                    Map<String, Object> row = new HashMap<>();
                    row.put("akCode", akCode);
                    row.put("currentRpm", rpm);
                    row.put("currentQps", qps);
                    row.put("limitQps", limitQps);
                    items.add(row);
                } catch (Exception e) {
                    log.warn("Failed to process key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Error in listTopAkByQps", e);
        }

        return items.stream()
                .sorted(Comparator.comparingDouble(o -> -((Double) o.get("currentQps"))))
                .limit(topN)
                .collect(Collectors.toList());
    }
    
    public void incrementConcurrentCount(String akCode, String entityCode) {
        String concurrentKey = String.format(CONCURRENT_KEY_FORMAT, entityCode, akCode);
        List<Object> keys = Lists.newArrayList(concurrentKey);
        List<Object> params = new ArrayList<>();
        params.add("INCR");
        try {
            executor.execute("/concurrent", ScriptType.limiter, keys, params);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }
    
    public void decrementConcurrentCount(String akCode, String entityCode) {
        String concurrentKey = String.format(CONCURRENT_KEY_FORMAT, entityCode, akCode);
        List<Object> keys = Lists.newArrayList(concurrentKey);
        List<Object> params = new ArrayList<>();
        params.add("DECR");
        try {
            executor.execute("/concurrent", ScriptType.limiter, keys, params);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public Long getCurrentConcurrentCount(String akCode, String entityCode) {
        String concurrentKey = String.format(CONCURRENT_KEY_FORMAT, entityCode, akCode);
        return getCountFromRedis(concurrentKey);
    }

    // ========== 私有辅助方法（消除重复代码） ==========

    /**
     * 执行 RPM Lua 脚本
     * @param rpmKey Redis sorted set key
     * @param countKey Redis count key
     * @param requestId 请求ID
     * @param currentTimestamp 当前时间戳
     */
    private void executeRpmScript(String rpmKey, String countKey, String requestId, long currentTimestamp) {
        List<Object> keys = Lists.newArrayList(rpmKey, countKey);
        List<Object> params = new ArrayList<>();
        params.add(currentTimestamp);
        params.add(requestId);

        try {
            executor.execute("/rpm", ScriptType.limiter, keys, params);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    /**
     * 从 Redis 获取计数值
     * @param countKey Redis count key
     * @return 计数值，不存在时返回 0
     */
    private Long getCountFromRedis(String countKey) {
        Object count = redisson.getBucket(countKey).get();
        return count != null ? Long.parseLong(count.toString()) : 0L;
    }
}
