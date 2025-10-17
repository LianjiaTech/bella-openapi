package com.ke.bella.openapi.worker;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@SuppressWarnings("all")
public class ChannelIdleDetector {

    @Resource
    private MetricsManager metricsManager;

    @Resource
    private LuaScriptExecutor executor;

    @Resource
    private RedissonClient redisson;

    @Value("${bella.openapi.as-worker.remaining-capacity.threshold:0.7}")
    private double remainingCapacityThreshold;

    private static final String RPM_THRESHOLD_KEY_FORMAT = "bella-openapi-channel-metrics:%s:rpm_threshold";
    private static final long DEFAULT_AVG_RESPONSE_TIME_MS = 60 * 1000;

    private final Map<String, EmaRpmCalculator> emaHistoryCache = new ConcurrentHashMap<>();
    private final Cache<String, Integer> thresholdCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    public void recordRpm(EndpointProcessData processData) {
        try {
            String channelCode = processData.getChannelCode();
            long currentTimestamp = DateTimeUtils.getCurrentSeconds();
            long avgResponseTime = getAverageResponseTime(channelCode);

            List<Object> params = Arrays.asList(currentTimestamp, avgResponseTime);
            List<Object> keys = Collections.singletonList(channelCode);
            Object result = executor.execute("/realtime_rpm", ScriptType.limiter, keys, params);

            Map<String, Object> data = JacksonUtils.toMap(result.toString());
            double currentRpm = MapUtils.getDoubleValue(data, "current_rpm", 0.0);
            long timestamp = MapUtils.getLongValue(data, "timestamp", currentTimestamp);

            EmaRpmCalculator emaRpmCalculator = emaHistoryCache.computeIfAbsent(channelCode,
                    k -> new EmaRpmCalculator(channelCode, redisson));
            emaRpmCalculator.updateEmaRpm(currentRpm, timestamp);
        } catch (Exception e) {
            log.warn("Failed to update realtime RPM for channel: {}", processData.getChannelCode(), e);
        }
    }

    public boolean hasEnoughCapacity(String channelCode) {
        try {
            Set<String> unavailableChannels = metricsManager.getAllUnavailableChannels(Collections.singletonList(channelCode));
            if(unavailableChannels.contains(channelCode)) {
                return false;
            }

            EmaRpmCalculator emaRpmCalculator = emaHistoryCache.get(channelCode);
            if(emaRpmCalculator == null) {
                return true;
            }

            Integer maxRpmThreshold = getMaxRpmThreshold(channelCode);
            if(maxRpmThreshold == null || maxRpmThreshold <= 0) {
                return true;
            }

            long currentTime = DateTimeUtils.getCurrentSeconds();
            double currentEma = emaRpmCalculator.getCurrentEma(currentTime);

            double loadRatio = currentEma / maxRpmThreshold;
            double remainingCapacity = Math.max(0.0, 1.0 - loadRatio);

            return remainingCapacity >= remainingCapacityThreshold;
        } catch (Exception e) {
            log.warn("Failed to check remaining capacity for channel: {}, fallback to no capacity", channelCode, e);
            return false;
        }
    }

    private Integer getMaxRpmThreshold(String channelCode) {
        try {
            Integer cached = thresholdCache.getIfPresent(channelCode);
            if(cached != null) {
                return cached;
            }
            String rpmThresholdKey = String.format(RPM_THRESHOLD_KEY_FORMAT, channelCode);
            Object maxRpmObj = redisson.getBucket(rpmThresholdKey).get();
            if(maxRpmObj != null) {
                Integer threshold = Integer.parseInt(maxRpmObj.toString());
                thresholdCache.put(channelCode, threshold);
                return threshold;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private long getAverageResponseTime(String channelCode) {
        try {
            Map<String, Map<String, Object>> metrics = metricsManager.queryMetrics("/v1/chat/completions"
                    , Collections.singletonList(channelCode));
            Map<String, Object> channelMetrics = metrics.get(channelCode);
            if(channelMetrics == null) {
                return DEFAULT_AVG_RESPONSE_TIME_MS;
            }

            Object ttltObj = channelMetrics.get("ttlt");
            Object completedObj = channelMetrics.get("completed");
            if(ttltObj == null || completedObj == null) {
                return DEFAULT_AVG_RESPONSE_TIME_MS;
            }

            long ttlt = Long.parseLong(ttltObj.toString());
            long completed = Long.parseLong(completedObj.toString());
            if(completed > 0) {
                return ttlt / completed;
            } else {
                return DEFAULT_AVG_RESPONSE_TIME_MS;
            }
        } catch (Exception e) {
            log.warn("Failed to get average response time for channel: {}, using default", channelCode, e);
            return DEFAULT_AVG_RESPONSE_TIME_MS;
        }
    }
}
