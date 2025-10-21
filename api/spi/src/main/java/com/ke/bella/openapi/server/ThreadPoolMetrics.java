package com.ke.bella.openapi.server;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * ThreadPoolExecutor 监控指标
 *
 * 监控指标:
 * - okhttp.threadpool.active: 活跃线程数
 * - okhttp.threadpool.size: 当前线程池大小
 * - okhttp.threadpool.max: 最大线程数
 * - okhttp.threadpool.core: 核心线程数
 * - okhttp.threadpool.queue.size: 队列中任务数
 * - okhttp.threadpool.queue.capacity: 队列容量
 *
 * 查看具体指标值:
 * curl http://localhost:8080/actuator/metrics/okhttp.threadpool.active
 * curl http://localhost:8080/actuator/metrics/okhttp.threadpool.max
 * curl http://localhost:8080/actuator/metrics/okhttp.threadpool.queue.size
 */
@Slf4j
public class ThreadPoolMetrics implements MeterBinder {

    private final ThreadPoolExecutor threadPoolExecutor;
    private final String poolName;

    public ThreadPoolMetrics(ThreadPoolExecutor threadPoolExecutor) {
        this(threadPoolExecutor, "default");
    }

    public ThreadPoolMetrics(ThreadPoolExecutor threadPoolExecutor, String poolName) {
        this.threadPoolExecutor = threadPoolExecutor;
        this.poolName = poolName;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        // 活跃线程数
        Gauge.builder("okhttp.threadpool.active", threadPoolExecutor, pool -> {
            try {
                return pool.getActiveCount();
            } catch (Exception e) {
                log.warn("Failed to get active count", e);
                return 0;
            }
        })
        .tag("pool", poolName)
        .description("Number of threads actively executing tasks")
        .register(registry);

        // 当前线程池大小
        Gauge.builder("okhttp.threadpool.size", threadPoolExecutor, pool -> {
            try {
                return pool.getPoolSize();
            } catch (Exception e) {
                log.warn("Failed to get pool size", e);
                return 0;
            }
        })
        .tag("pool", poolName)
        .description("Current number of threads in the pool")
        .register(registry);

        // 最大线程数
        Gauge.builder("okhttp.threadpool.max", threadPoolExecutor, pool -> {
            try {
                return pool.getMaximumPoolSize();
            } catch (Exception e) {
                log.warn("Failed to get maximum pool size", e);
                return 0;
            }
        })
        .tag("pool", poolName)
        .description("Maximum allowed number of threads")
        .register(registry);

        // 核心线程数
        Gauge.builder("okhttp.threadpool.core", threadPoolExecutor, pool -> {
            try {
                return pool.getCorePoolSize();
            } catch (Exception e) {
                log.warn("Failed to get core pool size", e);
                return 0;
            }
        })
        .tag("pool", poolName)
        .description("Core number of threads")
        .register(registry);

        // 队列中任务数
        Gauge.builder("okhttp.threadpool.queue.size", threadPoolExecutor, pool -> {
            try {
                return pool.getQueue().size();
            } catch (Exception e) {
                log.warn("Failed to get queue size", e);
                return 0;
            }
        })
        .tag("pool", poolName)
        .description("Number of tasks currently queued")
        .register(registry);

        // 队列容量
        Gauge.builder("okhttp.threadpool.queue.capacity", threadPoolExecutor, pool -> {
            try {
                return pool.getQueue().size() + pool.getQueue().remainingCapacity();
            } catch (Exception e) {
                log.warn("Failed to get queue capacity", e);
                return 0;
            }
        })
        .tag("pool", poolName)
        .description("Maximum capacity of the queue")
        .register(registry);
    }
}
