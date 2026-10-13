package com.ke.bella.openapi.protocol.cost;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;

import com.ke.bella.openapi.utils.DateTimeUtils;

public class CostCounter {
    private final CostRecorder costRecorder;
    private final ConcurrentHashMap<String, AtomicReference<BigDecimal>> costCache = new ConcurrentHashMap<>();

    public CostCounter(CostRecorder costRecorder) {
        this.costRecorder = costRecorder;
    }

    public void delta(String apikey, BigDecimal cost) {
        AtomicReference<BigDecimal> amount = costCache.computeIfAbsent(apikey, k -> new AtomicReference<>(BigDecimal.ZERO));
        amount.accumulateAndGet(cost, BigDecimal::add);
    }

    @Scheduled(fixedRate = 60000)
    public synchronized void flush() {
        if(!costCache.isEmpty()) {
            Set<String> current = costCache.keySet();
            String month = DateTimeUtils.getCurrentMonth();
            current.forEach(apikey -> {
                AtomicReference<BigDecimal> cost = costCache.remove(apikey);
                costRecorder.recordCost(apikey, month, cost.get());
            });
        }
    }

    public interface CostRecorder {
        void recordCost(String apikey, String month, BigDecimal cost);
    }
}
