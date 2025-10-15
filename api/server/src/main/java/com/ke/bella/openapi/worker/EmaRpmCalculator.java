package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("all")
public class EmaRpmCalculator {

    private final String channelCode;
    private final ConcurrentLinkedDeque<RpmRecord> history;
    private final ReentrantReadWriteLock lock;
    private final RedissonClient redissonClient;

    private volatile double lastEma;
    private volatile long lastUpdateTime;
    private volatile double lastRecordedRpm;
    private volatile long lastRecordTime;

    private static final int HISTORY_SIZE = 10; // 历史记录保存数量
    private static final double ALPHA = 2.0 / (HISTORY_SIZE + 1); // EMA平滑系数
    private static final int RECORD_MAX_INTERVAL = 120; // 最大记录间隔(秒)
    private static final double RECORD_CHANGE_THRESHOLD = 0.15; // RPM变化阈值
    private static final int DECAY_HALF_LIFE = 180; // 时间衰减半衰期(秒)
    private static final double QUICK_CALC_CURRENT_WEIGHT = 0.3; // 快速计算当前值权重
    private static final double QUICK_CALC_DECAYED_WEIGHT = 0.7; // 快速计算衰减EMA权重

    private static final String REDIS_KEY_PREFIX = "bella-openapi:channel:ema_rpm:";
    private static final long EXPIRE_TIME = 7 * 24 * 60 * 60; // 7天过期

    public EmaRpmCalculator(String channelCode, RedissonClient redissonClient) {
        this.channelCode = channelCode;
        this.history = new ConcurrentLinkedDeque<>();
        this.lock = new ReentrantReadWriteLock();
        this.redissonClient = redissonClient;
        this.lastEma = 0.0;
        this.lastUpdateTime = 0L;
        this.lastRecordedRpm = 0.0;
        this.lastRecordTime = 0L;

        loadState();
        TaskExecutor.scheduleAtFixedRate(this::persistState, 60 * 5);
    }

    public void updateEmaRpm(double currentRpm, long timestamp) {
        if(shouldRecordRpm(currentRpm, timestamp)) {
            if(history.isEmpty()) {
                RpmRecord zeroRecord = new RpmRecord(timestamp, 0.0);
                for (int i = 0; i < HISTORY_SIZE; i++) {
                    history.add(zeroRecord);
                }
            }

            history.addFirst(new RpmRecord(timestamp, currentRpm));

            while (history.size() > HISTORY_SIZE) {
                history.removeLast();
            }

            double emaValue = calculateEma(timestamp);

            lastEma = Math.floor(emaValue * 100 + 0.5) / 100;
            lastUpdateTime = timestamp;
            lastRecordedRpm = currentRpm;
            lastRecordTime = timestamp;
        } else {
            long timeDiff = timestamp - lastUpdateTime;
            double decayedEma = lastEma * calculateDecayFactor(timeDiff);

            double adjustedEma = QUICK_CALC_CURRENT_WEIGHT * currentRpm +
                    QUICK_CALC_DECAYED_WEIGHT * decayedEma;

            lastEma = Math.floor(adjustedEma * 100 + 0.5) / 100;
            lastUpdateTime = timestamp;
        }
    }

    public double getCurrentEma(long currentTime) {
        long updateTime = lastUpdateTime;
        double ema = lastEma;

        if(updateTime == 0) {
            return 0.0;
        }

        long timeDiff = currentTime - updateTime;
        if(timeDiff <= 0) {
            return ema;
        }

        return ema * calculateDecayFactor(timeDiff);
    }

    private boolean shouldRecordRpm(double currentRpm, long timestamp) {
        long timeDiff = timestamp - lastRecordTime;

        if(timeDiff >= RECORD_MAX_INTERVAL) {
            return true;
        }

        if(lastRecordedRpm == 0.0 && currentRpm > 0) {
            return true;
        }

        if(lastRecordedRpm > 0) {
            double changeRatio = Math.abs(currentRpm - lastRecordedRpm) / lastRecordedRpm;
            return changeRatio >= RECORD_CHANGE_THRESHOLD;
        }

        return false;
    }

    private double calculateDecayFactor(long timeDiff) {
        return Math.pow(0.5, (double) timeDiff / DECAY_HALF_LIFE);
    }

    private double calculateEma(long currentTime) {
        if(history.isEmpty()) {
            return 0.0;
        }

        List<Double> decayedValues = history.stream()
                .mapToDouble(record -> record.getDecayedRpm(currentTime, DECAY_HALF_LIFE))
                .boxed()
                .collect(Collectors.toList());

        if(decayedValues.isEmpty()) {
            return 0.0;
        }

        double ema = decayedValues.get(decayedValues.size() - 1); // 最后一个是最老的
        for (int i = decayedValues.size() - 2; i >= 0; i--) {
            ema = ALPHA * decayedValues.get(i) + (1 - ALPHA) * ema;
        }

        return ema;
    }

    private void persistState() {
        String key = REDIS_KEY_PREFIX + channelCode;
        lock.readLock().lock();
        try {
            List<RpmRecord> historyRecords = history.stream().collect(Collectors.toList());
            EmaRpmSnapshot state = new EmaRpmSnapshot(lastEma, lastUpdateTime, lastRecordedRpm, lastRecordTime, historyRecords);
            String json = JacksonUtils.serialize(state);
            redissonClient.getBucket(key).set(json, EXPIRE_TIME, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to persist EMA state to Redis for channel {}", channelCode, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void loadState() {
        lock.writeLock().lock();
        try {
            String json = (String) redissonClient.getBucket(REDIS_KEY_PREFIX + channelCode).get();
            if(json == null) {
                return;
            }

            EmaRpmSnapshot snapshot = JacksonUtils.deserialize(json, EmaRpmSnapshot.class);
            this.lastEma = snapshot.getLastEma();
            this.lastUpdateTime = snapshot.getLastUpdateTime();
            this.lastRecordedRpm = snapshot.getLastRecordedRpm();
            this.lastRecordTime = snapshot.getLastRecordTime();

            this.history.clear();
            this.history.addAll(snapshot.getHistory());

            log.info("Restored EMA state for channel {}: lastEma={}, historySize={}",
                    channelCode, lastEma, history.size());
        } catch (Exception e) {
            log.error("Failed to load EMA state from Redis for channel {}", channelCode, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Getter
    public static class RpmRecord {
        private final long timestamp;
        private final double rpm;

        public RpmRecord(long timestamp, double rpm) {
            this.timestamp = timestamp;
            this.rpm = rpm;
        }

        public double calculateDecayFactor(long currentTime, int halfLife) {
            long timeDiff = currentTime - timestamp;
            if(timeDiff <= 0) {
                return 1.0;
            }
            return Math.pow(0.5, (double) timeDiff / halfLife);
        }

        public double getDecayedRpm(long currentTime, int halfLife) {
            return rpm * calculateDecayFactor(currentTime, halfLife);
        }
    }

    @Getter
    public static class EmaRpmSnapshot {
        private final double lastEma;
        private final long lastUpdateTime;
        private final double lastRecordedRpm;
        private final long lastRecordTime;
        private final List<RpmRecord> history;

        public EmaRpmSnapshot(double lastEma, long lastUpdateTime, double lastRecordedRpm,
                long lastRecordTime, List<RpmRecord> history) {
            this.lastEma = lastEma;
            this.lastUpdateTime = lastUpdateTime;
            this.lastRecordedRpm = lastRecordedRpm;
            this.lastRecordTime = lastRecordTime;
            this.history = history;
        }
    }

}
