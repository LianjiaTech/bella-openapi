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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * CapacityCalculator estimates channel capacity based on historical 429 rate limit events.
 *
 * This class is a core component of the worker queue system, responsible for determining
 * how much remaining capacity a channel has before reaching rate limits. It uses:
 *
 * 1. Historical 429 (rate limit) data to build an accurate capacity model
 * 2. Exponential Moving Average (EMA) for smoothing noisy rate limit signals
 * 3. Three-tier fallback strategy to handle cold start and sparse data scenarios
 *
 * The capacity calculation considers both:
 * - Completed requests in the last minute (actual throughput)
 * - Current in-flight requests (converted to estimated RPM impact)
 *
 * Thread Safety: This class uses volatile fields for caching and is designed to be
 * safely called from multiple worker threads concurrently.
 */
@Slf4j
@SuppressWarnings("all")
public class CapacityCalculator {

    private String channelCode;
    private ChannelDB channel;
    private CapacityFittingAlgorithm fittingAlgorithm;
    private RedissonClient redissonClient;
    private LimiterManager limiterManager;
    private LuaScriptExecutor luaScriptExecutor;

    // Cached capacity value to reduce Redis queries (thread-safe via volatile)
    private volatile double cachedCapacity = -1.0;
    private volatile long cacheTimestamp = 0;

    // Track historical peak RPM for fallback capacity estimation
    private volatile long maxFinishRpm = 0;

    // Cache capacity for 5 minutes to balance freshness and Redis load
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000;

    // Redis key suffix for 429 rate limit history
    private static final String RPM_429_HISTORY_METRIC = "rpm_429_history";

    public CapacityCalculator(ChannelDB channelDB, RedissonClient redissonClient) {
        this.channel = channelDB;
        this.channelCode = channelDB.getChannelCode();
        this.fittingAlgorithm = new EmaFittingAlgorithm(0.3);
        this.redissonClient = redissonClient;
    }

    public CapacityCalculator(ChannelDB channelDB, RedissonClient redissonClient, LuaScriptExecutor luaScriptExecutor) {
        this.channel = channelDB;
        this.channelCode = channelDB.getChannelCode();
        this.fittingAlgorithm = new EmaFittingAlgorithm(0.3);
        this.redissonClient = redissonClient;
        this.luaScriptExecutor = luaScriptExecutor;
    }

    /**
     * Calculate remaining capacity ratio based on current channel utilization.
     *
     * Formula: remainingCapacity = 1.0 - (totalUtilization / estimatedCapacity)
     *
     * Key Insight: RPM limits are enforced on "request start time" within a 60-second sliding window.
     * Therefore, total utilization = completed requests (in last 60s) + in-flight requests (not yet completed).
     * Both dimensions use the same unit (request count), so they can be directly summed.
     *
     * Example:
     * - Completed in last 60s: 500 requests
     * - Currently processing: 10 requests
     * - Total utilization: 510 requests started in the last minute
     * - If capacity is 600 RPM, remaining = 1.0 - (510/600) = 15%
     *
     * Three-tier fallback strategy for capacity estimation:
     * 1. EMA-fitted capacity from 429 rate limit history (most accurate)
     * 2. Conservative estimate: 70% of historical peak RPM (safe fallback)
     * 3. Cold start: 20% capacity to avoid overwhelming the channel (protective measure)
     *
     * @return remaining capacity ratio between 0.0 (fully utilized) and 1.0 (completely idle)
     */
    public double getRemainingCapacity() {
        // Level 1: Try to get EMA-fitted capacity from rate limit history
        double capacity = getCapacity();

        // Level 2: Fallback to 70% of peak RPM if no 429 history exists
        if(capacity == 0) {
            capacity = 0.7 * getCurrentMaxRpm();
        }

        // Level 3: Cold start protection - use conservative 20% capacity estimate
        // This prevents overwhelming the channel during initial worker startup
        if(capacity == 0) {
            return 0.2;
        }

        // Calculate current utilization within the 1-minute sliding window
        // RPM limits are typically enforced on "request start time", meaning:
        // - Total requests started in the last 60 seconds
        // - = Completed requests (in last 60s) + In-flight requests (started but not finished)
        long completedInWindow = getCompletedRpm();  // Completed in last 60 seconds
        long inFlightRequests = getCurrentRequests(); // Currently processing

        // Total utilization = all requests started in the last 60 seconds
        // This matches how most AI providers enforce RPM limits (by request start time)
        long totalUtilization = completedInWindow + inFlightRequests;

        // Calculate remaining capacity ratio
        double remainingCapacity = 1.0 - ((double)totalUtilization / capacity);

        // Clamp result to [0.0, 1.0] range
        return Math.max(0.0, Math.min(1.0, remainingCapacity));
    }

    /**
     * Get historical peak RPM for this channel.
     *
     * Tracks the maximum observed completed RPM across the lifetime of this calculator
     * instance. Used as a fallback capacity estimate when no 429 history exists.
     *
     * @return maximum completed RPM observed so far
     */
    private long getCurrentMaxRpm() {
        long currentRpm = getCompletedRpm();

        // Update peak if current exceeds historical maximum
        if(currentRpm > maxFinishRpm) {
            maxFinishRpm = currentRpm;
        }

        return Math.max(currentRpm, maxFinishRpm);
    }

    /**
     * Get completed request count in the last 60 seconds.
     *
     * Important: This returns the ABSOLUTE COUNT of completed requests, not a rate.
     * Example: If 600 requests completed in the last 60 seconds, this returns 600.
     *
     * The method name "getCompletedRpm" is somewhat misleading - it's actually
     * "completed count within the RPM time window (60 seconds)".
     *
     * Calls the get_completed.lua script which automatically purges expired data
     * and returns the sliding window count for the last minute.
     *
     * @return absolute count of completed requests in the last 60 seconds
     */
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

    /**
     * Get current in-flight concurrent requests for this channel.
     *
     * Reads the channel-level concurrent counter maintained by the concurrent.lua script.
     * This represents requests currently being processed (not yet completed).
     *
     * @return number of concurrent requests in progress
     */
    private long getCurrentRequests() {
        String concurrentKey = "bella-openapi-channel-concurrent:" + channel.getEntityCode();
        Object count = redissonClient.getBucket(concurrentKey).get();
        return count != null ? Long.parseLong(count.toString()) : 0L;
    }

    /**
     * Get channel capacity with 5-minute caching.
     *
     * This method caches the computed capacity to reduce Redis load, as capacity
     * estimation from 429 history is relatively expensive and doesn't change frequently.
     *
     * @return estimated channel capacity in RPM
     */
    public double getCapacity() {
        long currentTime = System.currentTimeMillis();

        // Return cached value if still fresh (within 5 minutes)
        if(cachedCapacity >= 0 && (currentTime - cacheTimestamp) < CACHE_DURATION_MS) {
            return cachedCapacity;
        }

        // Cache expired or not yet initialized, recompute capacity
        double capacity = computeCapacity();
        cachedCapacity = capacity;
        cacheTimestamp = currentTime;
        return capacity;
    }

    /**
     * Compute channel capacity from 429 rate limit history using EMA algorithm.
     *
     * The history is stored in Redis using LPUSH (newest data at head), so we need to
     * reverse the list to ensure EMA processes data from oldest to newest for correct
     * exponential smoothing.
     *
     * @return estimated capacity in RPM, or 0.0 if no history available
     */
    private double computeCapacity() {
        String historyKey = "bella-openapi-channel-metrics:" + channelCode + ":" + RPM_429_HISTORY_METRIC;

        // Retrieve all 429 history data points from Redis
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

    /**
     * Parse a 429 metrics data point from Redis JSON string.
     *
     * Each data point contains:
     * - timestamp: Unix epoch seconds when the 429 error occurred
     * - rpm: Requests per minute at the time of rate limiting
     * - avg_response_time: Average response time in milliseconds
     *
     * @param metric JSON string from Redis list
     * @return parsed MetricsPoint, or null if parsing fails
     */
    private MetricsPoint parseMetrics(Object metric) {
        try {
            Map<String, Object> data = JacksonUtils.toMap(metric.toString());
            Long timestamp = MapUtils.getLong(data, "timestamp");
            Double rpm = MapUtils.getDouble(data, "rpm");
            // Default to 60 seconds if avg_response_time is missing
            Double avgResponseTime = MapUtils.getDouble(data, "avg_response_time", 60 * 1000.0);
            return new MetricsPoint(timestamp, rpm, avgResponseTime);
        } catch (Exception e) {
            log.warn("Failed to parse data point: {}", metric, e);
            return null;
        }
    }

    /**
     * Data point representing a 429 rate limit event.
     *
     * Captured when the channel receives a 429 HTTP status, indicating we've
     * reached the provider's rate limit. This data is used to estimate the
     * maximum safe throughput for the channel.
     */
    @Data
    @AllArgsConstructor
    public static class MetricsPoint {
        private long timestamp;           // Unix epoch seconds
        private double rpm;                // Requests per minute at rate limit
        private double avgResponseTime;    // Average response time (ms)
    }

    public interface CapacityFittingAlgorithm {
        double calculateCapacity(List<MetricsPoint> historyData);

        String getAlgorithmName();
    }

    /**
     * Exponential Moving Average (EMA) algorithm for capacity estimation.
     *
     * EMA is a time-series smoothing technique that gives exponentially decreasing weights
     * to older observations. This helps reduce noise in 429 rate limit data while being
     * responsive to recent changes.
     *
     * Formula: EMA(t) = α × RPM(t) + (1-α) × EMA(t-1)
     * where α (alpha) is the smoothing factor (0 < α < 1)
     *
     * Higher α values (e.g., 0.5) react faster to changes but are more sensitive to noise.
     * Lower α values (e.g., 0.3) provide more stable estimates but lag behind rapid changes.
     */
    public static class EmaFittingAlgorithm implements CapacityFittingAlgorithm {
        private final double alpha;

        private static final double DEFAULT_EMA_ALPHA = 0.3;
        private static final int DEFAULT_HISTORY_SIZE = 10;
        private static final int DEFAULT_RECORD_MAX_INTERVAL = 120;
        private static final double DEFAULT_RECORD_CHANGE_THRESHOLD = 0.15;
        private static final int DEFAULT_DECAY_HALF_LIFE = 180;
        private static final double DEFAULT_QUICK_CALC_CURRENT_WEIGHT = 0.3;
        private static final double DEFAULT_QUICK_CALC_DECAYED_WEIGHT = 0.7;

        /**
         * @param alpha smoothing factor (0 < α < 1). Default is 0.3 for stable estimation.
         */
        public EmaFittingAlgorithm(double alpha) {
            this.alpha = alpha;
        }

        /**
         * Calculate channel capacity using EMA on historical 429 rate limit data.
         *
         * IMPORTANT: Data must be ordered from oldest to newest for correct EMA calculation.
         * Since Redis stores data with LPUSH (newest first), we need to reverse the order.
         *
         * @param historyData list of 429 metrics points (will be reversed internally)
         * @return estimated capacity in RPM, rounded to nearest integer
         */
        @Override
        public double calculateCapacity(List<MetricsPoint> historyData) {
            if(historyData.isEmpty()) {
                return 0.0;
            }

            // Reverse the list to process from oldest to newest
            // Redis LPUSH stores newest data at index 0, but EMA needs chronological order
            List<MetricsPoint> chronologicalData = new java.util.ArrayList<>(historyData);
            java.util.Collections.reverse(chronologicalData);

            // Initialize EMA with the oldest data point
            double rpmEma = chronologicalData.get(0).getRpm();

            // Apply EMA formula: EMA(t) = α × RPM(t) + (1-α) × EMA(t-1)
            for (int i = 1; i < chronologicalData.size(); i++) {
                MetricsPoint point = chronologicalData.get(i);
                rpmEma = alpha * point.getRpm() + (1 - alpha) * rpmEma;
            }

            // Round to nearest integer RPM value
            return Math.floor(rpmEma + 0.5);
        }

        @Override
        public String getAlgorithmName() {
            return "ema";
        }
    }
}
