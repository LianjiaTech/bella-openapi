package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("all")
public class CapacityCalculator {

    private String channelCode;
    private ChannelDB channel;
    private CapacityFittingAlgorithm fittingAlgorithm;
    private RedissonClient redissonClient;
    private LimiterManager limiterManager;
    private LuaScriptExecutor luaScriptExecutor;

    private volatile double cachedCapacity = -1.0;
    private volatile long cacheTimestamp = 0;
    private volatile long maxFinishRpm = 0;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000;
    private static final String RPM_429_HISTORY_METRIC = "rpm_429_history";

    public CapacityCalculator(ChannelDB channelDB, RedissonClient redissonClient) {
        this.channel = channelDB;
        this.channelCode = channelDB.getChannelCode();
        this.fittingAlgorithm = new EmaFittingAlgorithm(0.3);
        this.redissonClient = redissonClient;
    }

    public CapacityCalculator(ChannelDB channelDB, RedissonClient redissonClient, LuaScriptExecutor luaScriptExecutor, LimiterManager limiterManager) {
        this.channel = channelDB;
        this.channelCode = channelDB.getChannelCode();
        this.fittingAlgorithm = new EmaFittingAlgorithm(0.3);
        this.redissonClient = redissonClient;
        this.luaScriptExecutor = luaScriptExecutor;
        this.limiterManager = limiterManager;
    }

    public double getRemainingCapacity() {
        double capacity = getCapacity();
        if(capacity == 0) {
            capacity = 0.7 * getCurrentMaxRpm();
        }
        if(capacity == 0) {
            return 1.0;
        }

        long currentRequests = limiterManager.getCurrentRequests(channel.getEntityCode());
        long requestCapacity = currentRequests + getCompletedRpm();
        double remainingCapacity = 1.0 - (requestCapacity / capacity);
        return Math.max(0.0, Math.min(1.0, remainingCapacity));
    }

    private long getCurrentMaxRpm() {
        long currentRpm = getCompletedRpm();

        if(currentRpm > maxFinishRpm) {
            maxFinishRpm = currentRpm;
        }

        return Math.max(currentRpm, maxFinishRpm);
    }

    private long getCompletedRpm() {
        try {
            List<Object> keys = Arrays.asList(channelCode);
            List<Object> args = Arrays.asList(DateTimeUtils.getCurrentSeconds());
            Object result = luaScriptExecutor.execute("/get_completed", ScriptType.metrics, keys, args);

            if(result != null) {
                return Long.parseLong(result.toString());
            }
        } catch (Exception e) {
            log.warn("Failed to get completed RPM for channel {}: {}", channelCode, e.getMessage());
        }
        return 0;
    }

    public double getCapacity() {
        long currentTime = System.currentTimeMillis();

        if(cachedCapacity >= 0 && (currentTime - cacheTimestamp) < CACHE_DURATION_MS) {
            return cachedCapacity;
        }

        double capacity = computeCapacity();
        cachedCapacity = capacity;
        cacheTimestamp = currentTime;
        return capacity;
    }

    private double computeCapacity() {
        String historyKey = "bella-openapi-channel-metrics:" + channelCode + ":" + RPM_429_HISTORY_METRIC;
        List<MetricsPoint> dataPoints = redissonClient.getList(historyKey).range(0, -1)
                .stream()
                .map(this::parseMetrics)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if(dataPoints.isEmpty()) {
            return 0.0;
        }

        return fittingAlgorithm.calculateCapacity(dataPoints);
    }

    private MetricsPoint parseMetrics(Object metric) {
        try {
            Map<String, Object> data = JacksonUtils.toMap(metric.toString());
            Long timestamp = MapUtils.getLong(data, "timestamp");
            Double rpm = MapUtils.getDouble(data, "rpm");
            Double avgResponseTime = MapUtils.getDouble(data, "avg_response_time", 60 * 1000.0);
            return new MetricsPoint(timestamp, rpm, avgResponseTime);
        } catch (Exception e) {
            log.warn("Failed to parse data point: {}", metric, e);
            return null;
        }
    }

    @Data
    @AllArgsConstructor
    public static class MetricsPoint {
        private long timestamp;
        private double rpm;
        private double avgResponseTime;
    }

    public interface CapacityFittingAlgorithm {
        double calculateCapacity(List<MetricsPoint> historyData);

        String getAlgorithmName();
    }

    public static class EmaFittingAlgorithm implements CapacityFittingAlgorithm {
        private final double alpha;

        private static final double DEFAULT_EMA_ALPHA = 0.3;
        private static final int DEFAULT_HISTORY_SIZE = 10;
        private static final int DEFAULT_RECORD_MAX_INTERVAL = 120;
        private static final double DEFAULT_RECORD_CHANGE_THRESHOLD = 0.15;
        private static final int DEFAULT_DECAY_HALF_LIFE = 180;
        private static final double DEFAULT_QUICK_CALC_CURRENT_WEIGHT = 0.3;
        private static final double DEFAULT_QUICK_CALC_DECAYED_WEIGHT = 0.7;

        public EmaFittingAlgorithm(double alpha) {
            this.alpha = alpha;
        }

        @Override
        public double calculateCapacity(List<MetricsPoint> historyData) {
            if(historyData.isEmpty()) {
                return 0.0;
            }

            double rpmEma = historyData.get(0).getRpm();

            for (int i = 1; i < historyData.size(); i++) {
                MetricsPoint point = historyData.get(i);
                rpmEma = alpha * point.getRpm() + (1 - alpha) * rpmEma;
            }

            return Math.floor(rpmEma + 0.5);
        }

        @Override
        public String getAlgorithmName() {
            return "ema";
        }
    }
}
