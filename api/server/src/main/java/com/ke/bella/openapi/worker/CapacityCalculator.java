package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.redisson.api.RedissonClient;
import org.redisson.api.RList;
import org.redisson.api.RMap;

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

    public double getRemainingCapacity() {
        double capacity = getCapacity();
        if(capacity == 0) {
            capacity = 0.7 * getCurrentMaxRpm();
        }
        if(capacity == 0) {
            return 1.0;
        }

        long requestCapacity = getCurrentRequests() + getCurrentFinishRpm();
        double remainingCapacity = 1.0 - (requestCapacity / capacity);
        return Math.max(0.0, Math.min(1.0, remainingCapacity));
    }

    private long getCurrentMaxRpm() {
        long currentRpm = getCurrentFinishRpm();

        if(currentRpm > maxFinishRpm) {
            maxFinishRpm = currentRpm;
        }

        return Math.max(currentRpm, maxFinishRpm);
    }

    private long getCurrentFinishRpm() {
        String prefix = "bella-openapi-channel-metrics:" + channelCode;
        long expiredThreshold = System.currentTimeMillis() / 1000 - 60;

        RList<Object> timestamps = redissonClient.getList(prefix + ":timestamps");
        RMap<Object, Object> completedMetrics = redissonClient.getMap(prefix + ":completed");
        RMap<Object, Object> totalMap = redissonClient.getMap(prefix + ":total");

        long expiredCompleted = 0;
        while (!timestamps.isEmpty() && timestamps.get(0) != null) {
            long oldestTimestamp = Long.parseLong(timestamps.get(0).toString());
            if(oldestTimestamp > expiredThreshold)
                break;

            timestamps.remove(0);
            Object expiredValue = completedMetrics.remove(String.valueOf(oldestTimestamp));
            if(expiredValue != null) {
                expiredCompleted += Long.parseLong(expiredValue.toString());
            }
        }

        Object totalObj = totalMap.get("completed");
        long total = totalObj != null ? Long.parseLong(totalObj.toString()) : 0;

        if(expiredCompleted > 0) {
            totalMap.addAndGet("completed", -expiredCompleted);
            total -= expiredCompleted;
        }

        return Math.max(0, total);
    }

    private long getCurrentRequests() {
        String concurrentKey = "bella-openapi-channel-concurrent:" + channel.getEntityCode();
        Object count = redissonClient.getBucket(concurrentKey).get();
        return count != null ? Long.parseLong(count.toString()) : 0L;
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
