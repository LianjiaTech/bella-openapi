package com.ke.bella.job.queue.worker;

import com.ke.bella.job.queue.JobQueueClient;
import com.ke.bella.job.queue.api.entity.response.TaskResp;
import com.ke.bella.job.queue.api.enums.TaskStatusEnum;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class JobQueueWorker {
    private final ThreadFactory tf = new NamedThreadFactory("bella-job-queue-worker", true);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2, tf);
    private final String endpoint;
    private final String queueName;
    @Setter
    private TaskHandler taskHandler;

    private final JobQueueClient jobQueueClient;

    private LinkedBlockingQueue<Task> retryQueue;

    @Setter
    private int pollSize = 1;

    @Setter
    private int retryQueueSize = 1000;

    public JobQueueWorker(String url, String endpoint, String queueName) {
        this.jobQueueClient = JobQueueClient.getInstance(url);
        this.endpoint = endpoint;
        this.queueName = queueName;
    }

    public void start() {
        retryQueue = new LinkedBlockingQueue<>(retryQueueSize);
        scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                pollAndExecute();
            } catch (Exception e) {
                LOGGER.error("execute job-queue task occur error", e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduledExecutorService.shutdown();
    }

    private void pollAndExecute() {
        TaskResp.TaskGetResp pollResponse;
        do {
            while (!retryQueue.isEmpty()) {
                execute(retryQueue.poll());
            }
            pollResponse = jobQueueClient.poll(pollSize, endpoint, queueName);
            Optional.ofNullable(pollResponse.getData()).ifPresent(tasks -> tasks.forEach(task -> execute(Task.of(task, this))));
        } while (!pollResponse.isEmpty());
    }

    private void execute(Task task) {
        try {
            taskHandler.execute(task);
        } catch (Exception e) {
            markFailed(task.getTaskId(), "");
        }
    }

    public void markRetryLater(Task task) {
        retryQueue.offer(task);
    }

    public void markSucceed(String taskId, String result) {
        jobQueueClient.update(taskId, TaskStatusEnum.COMPLETED, result);
    }

    public void markFailed(String taskId, String result) {
        jobQueueClient.update(taskId, TaskStatusEnum.FAILED, result);
    }

    public static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final boolean isDaemon;
        private final Thread.UncaughtExceptionHandler handler;

        public NamedThreadFactory(String prefix, boolean isDaemon) {
            this(prefix, isDaemon, null);
        }

        public NamedThreadFactory(String prefix, boolean isDaemon, Thread.UncaughtExceptionHandler handler) {
            this.prefix = prefix;
            this.isDaemon = isDaemon;
            this.handler = handler;
        }

        @Override
        public Thread newThread(@NotNull Runnable r) {
            final Thread t = new Thread(r, String.format("%s%d", prefix, threadNumber.getAndIncrement()));
            t.setDaemon(isDaemon);
            if(this.handler != null) {
                t.setUncaughtExceptionHandler(handler);
            }
            return t;
        }
    }
}
