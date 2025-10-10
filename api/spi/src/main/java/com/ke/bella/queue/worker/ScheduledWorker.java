package com.ke.bella.queue.worker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.theokanning.openai.queue.Take;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ScheduledWorker {
    private final Worker worker;
    private final Properties properties;

    private volatile boolean started = false;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2,
            new ThreadFactoryBuilder().setNameFormat("bella-queue-worker-%d").setDaemon(true).build());

    public ScheduledWorker(Properties properties, Worker worker) {
        this.worker = worker;
        this.properties = properties;
    }

    public void start() {
        if(started) {
            log.warn("worker is already started");
            return;
        }

        Take take = Take.builder()
                .queues(properties.getQueues())
                .endpoint(properties.getEndpoint())
                .strategy(properties.getTakeStrategy())
                .build();

        executor.scheduleWithFixedDelay(() -> {
            try {
                run(take);
            } catch (Exception e) {
                log.error("worker run occur error", e);
            }
        }, 30, properties.scheduleWorkerDelayMs, TimeUnit.MILLISECONDS);

        started = true;
        log.info("worker started successfully");
    }

    private void run(Take take) {
        int takeSize;
        do {
            int remainingCapacity = worker.remainingCapacity();
            if(remainingCapacity <= 0) {
                return;
            }

            int size = Math.min(remainingCapacity, properties.getSize());
            take.setSize(size);
            takeSize = worker.takeAndRun(take);
        } while (takeSize > 0 && started);
    }

    public void stop() {
        if(!started) {
            log.warn("worker is already stopped");
            return;
        }

        started = false;
        try {
            executor.shutdown();
            if(!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("worker stopped successfully");
    }

    @Data
    public static class Properties {
        private boolean enabled;
        private String endpoint;
        private List<String> queues;
        private String takeStrategy;
        private int size = 1;
		private int scheduleWorkerDelayMs = 100;

		// Properties类中添加验证
		public void setScheduleWorkerDelayMs(int scheduleWorkerDelayMs) {
			if (scheduleWorkerDelayMs < 50) {
				throw new IllegalArgumentException("scheduleWorkerDelayMs must be at least 50ms");
			}
			this.scheduleWorkerDelayMs = scheduleWorkerDelayMs;
		}
    }

}
