package com.ke.bella.openapi;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskExecutor {
    static ThreadFactory tf = new NamedThreadFactory("bella-worker-", true);
    static ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1000, tf);

    static {
        executor.setRemoveOnCancelPolicy(true);
    }

    public static CompletableFuture<Void> submit(Runnable r) {
        return CompletableFuture.runAsync(r, executor);
    }

    public static void scheduleAtFixedRate(Runnable r, int period) {
        executor.scheduleAtFixedRate(r, 0, period, TimeUnit.SECONDS);
    }

    public static ScheduledFuture<?> schedule(Runnable r, long delay, TimeUnit timeUnit) {
        return executor.schedule(r, delay, timeUnit);
    }

    public static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final boolean isDaemon;
        private final UncaughtExceptionHandler handler;

        public NamedThreadFactory(String prefix, boolean isDaemon) {
            this(prefix, isDaemon, null);
        }

        public NamedThreadFactory(String prefix, boolean isDaemon, UncaughtExceptionHandler handler) {
            this.prefix = prefix;
            this.isDaemon = isDaemon;
            this.handler = handler;
        }

        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r, String.format("%s%d", prefix, threadNumber.getAndIncrement()));
            t.setDaemon(isDaemon);
            if(this.handler != null) {
                t.setUncaughtExceptionHandler(handler);
            }
            return t;
        }
    }

    public static void shutdown() {
        executor.shutdown();
        try {
            if(!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
