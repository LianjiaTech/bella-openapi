package com.ke.bella.queue.worker;

import com.google.common.collect.Maps;
import com.ke.bella.queue.TaskEvent;
import com.ke.bella.queue.TaskWrapper;
import com.theokanning.openai.queue.EventbusConfig;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.queue.Task;
import com.theokanning.openai.service.OpenAiService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.XAddParams;

import javax.annotation.PreDestroy;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

@Slf4j
public class Worker {
    private final TaskExecutor taskExecutor;
    private final OpenAiService openAiService;
    private JedisPool jedisPool;
    private EventbusConfig eventbus;
    private volatile boolean initialized = false;

    private final LinkedBlockingQueue<Task> retryQueue = new LinkedBlockingQueue<>(1000);

    public Worker(TaskExecutor taskExecutor, OpenAiService openAiService) {
        this.openAiService = openAiService;
        this.taskExecutor = taskExecutor;
        CompletableFuture.runAsync(this::initWithRetry);
    }

    @SneakyThrows
    private void initWithRetry() {
        final int maxRetries = 60;
        final long baseDelay = 1000L;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                init();
                initialized = true;
                return;
            } catch (Exception e) {
                log.warn("Redis connection attempt {}/{} failed: {}",
                        attempt, maxRetries, e.getMessage());

                if(attempt < maxRetries) {
                    Thread.sleep(baseDelay * attempt);
                }
            }
        }

        log.error("Redis connection failed after {} attempts", maxRetries);
    }

    @SneakyThrows
    private void init() {
        EventbusConfig eventbus = openAiService.getEventbus();
        this.jedisPool = new JedisPool(new JedisPoolConfig(), new URI(eventbus.getUrl()));
        this.eventbus = eventbus;
    }

    public int takeAndRun(Take take) {
        if(!initialized) {
            log.warn("Worker not initialized, cannot take tasks");
            return 0;
        }

        while (!retryQueue.isEmpty()) {
            run(retryQueue.poll());
        }

        List<Task> tasks = openAiService.takeTasks(take)
                .values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        tasks.forEach(this::run);
        return tasks.size();
    }

    private void run(Task task) {
        try {
            taskExecutor.submit(TaskWrapper.of(task, this));
        } catch (Exception e) {
            log.error("Task failed: {}", task.getTaskId(), e);
            Map<String, Object> body = Maps.newHashMap();
            body.put("error", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("status_code", 500);
            response.put("body", body);
            markComplete(task, response);
        }
    }

    public void markComplete(Task task, Map<String, Object> result) {
        openAiService.completeTask(task.getTaskId(), result);
    }

    public void markRetryLater(Task task) {
        retryQueue.offer(task);
    }

    public void emitProgress(String instanceId, TaskWrapper.Event<TaskEvent.Progress.Payload> event) {
        sendMessage(instanceId, event);
    }

    public void emitCompletion(String instanceId, TaskWrapper.Event<TaskEvent.Completion.Payload> event) {
        sendMessage(instanceId, event);
    }

    private void sendMessage(String instanceId, TaskWrapper.Event<?> event) {
        String streamKey = eventbus.getTopic() + instanceId;
        long minIdTimestamp = System.currentTimeMillis() - 60_000;

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.xadd(streamKey,
                    XAddParams.xAddParams().minId(String.valueOf(minIdTimestamp)),
                    event.toMap());
        } catch (JedisException e) {
            log.error("Failed to send message to stream: {}", streamKey, e);
            throw new RuntimeException("Failed to send message to stream: " + streamKey, e);
        }
    }

    public Integer remainingCapacity() {
        return taskExecutor.remainingCapacity();
    }

    @PreDestroy
    public void close() {
        if(jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

}
