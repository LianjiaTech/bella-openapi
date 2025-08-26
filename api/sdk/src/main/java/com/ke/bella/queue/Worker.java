package com.ke.bella.queue;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.ke.bella.queue.remote.QueueClient;
import com.ke.bella.queue.remote.QueueOps;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class Worker {

    private final QueueClient client;
    @Setter
    private WorkerConfig config;
    @Setter
    private Function<Task, Object> taskHandler;

    private volatile boolean started = false;

    private final LinkedBlockingQueue<QueueOps.Task> retryQueue = new LinkedBlockingQueue<>(1000);
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2,
            new ThreadFactoryBuilder().setNameFormat("bella-queue-worker-%d").setDaemon(true).build());

    public Worker(WorkerConfig config) {
        this.config = config;
        this.client = QueueClient.getInstance(config.getServiceUrl());
    }

    public synchronized void start() {
        if(started) {
            LOGGER.warn("worker is already started");
            return;
        }

        executorService.scheduleWithFixedDelay(() -> {
            try {
                run();
            } catch (Exception e) {
                LOGGER.error("run occur error", e);
            }
        }, 0, 5, TimeUnit.SECONDS);

        started = true;
        LOGGER.info("worker started successfully");
    }

    private void run() {
        QueueOps.Take takeParam = QueueOps.Take.of(config);
        List<QueueOps.Task> tasks;
        do {
            while (!retryQueue.isEmpty()) {
                execute(retryQueue.poll());
            }

            takeParam.setQueues(config.getQueues());
            tasks = client.take(takeParam, config.getConsoleKey())
                    .values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            tasks.forEach(this::execute);
        } while (!tasks.isEmpty());
    }

    private void execute(QueueOps.Task task) {
        try {
            Object result = taskHandler.apply(Task.of(task, this));
            markComplete(task, result);
        } catch (RetryException e) {
            markRetryLater(task);
        } catch (Exception e) {
            markComplete(task, e.getMessage());
        }
    }

    public void markRetryLater(QueueOps.Task task) {
        retryQueue.offer(task);
    }

    public void markComplete(QueueOps.Task task, Object result) {
        if(!task.getResponseMode().equals("streaming")) {
            client.complete(task.getTaskId(), result, config.getConsoleKey());
        }
    }

    public synchronized void stop() {
        if(!started) {
            LOGGER.warn("worker is already stopped");
            return;
        }
        started = false;
        try {
            executorService.shutdown();
            if(!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("worker stopped successfully");
    }

}
