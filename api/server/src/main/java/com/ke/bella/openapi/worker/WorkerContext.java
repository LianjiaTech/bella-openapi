package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.worker.Worker;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.service.OpenAiService;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

import java.util.Collections;
import java.util.Objects;

@Slf4j
@Builder
@SuppressWarnings("all")
public class WorkerContext {

    private static final String QUEUE_NAME_TEMPLATE = "%s:1";

    private final ChannelDB channel;
    private final RedissonClient redissonClient;
    private final OpenAiService openAiService;
    private final OpenapiClient openapiClient;
    private final AdaptorManager adaptorManager;
    private final LuaScriptExecutor luaScriptExecutor;
    private final LimiterManager limiterManager;

    private volatile BackoffTask backoffTask;

    public void start() {
        TaskProcessor taskProcessor = TaskProcessor.builder()
                .channel(channel)
                .adaptorManager(adaptorManager)
                .openapiClient(openapiClient)
                .build();
        Worker worker = new Worker(taskProcessor::executeTask, openAiService);
        CapacityCalculator capacityCalculator = new CapacityCalculator(channel, redissonClient, luaScriptExecutor, limiterManager);
        backoffTask = new BackoffTask(worker, capacityCalculator, channel, redissonClient);
        TaskExecutor.submit(backoffTask);
    }

    public void stop() {
        if(backoffTask != null) {
            backoffTask.stop();
        }
    }

    public boolean isStopped() {
        return backoffTask == null || backoffTask.isStopped();
    }

    public boolean isSameQueue(String queueName) {
        return Objects.equals(this.channel.getQueueName(), queueName);
    }

    public String getChannelCode() {
        return channel.getChannelCode();
    }

    @Slf4j
    public static class BackoffTask implements Runnable {
        private final Worker worker;
        private OpenapiClient openapiClient;
        private final RedissonClient redissonClient;
        private final CapacityCalculator capacityCalculator;
        private final ChannelDB channel;
        private final BackoffState backoff = new BackoffState();

        private volatile boolean stopped = false;

        public BackoffTask(Worker worker, CapacityCalculator capacityCalculator, ChannelDB channel, RedissonClient redissonClient) {
            this.worker = worker;
            this.capacityCalculator = capacityCalculator;
            this.channel = channel;
            this.redissonClient = redissonClient;
        }

        /**
         * Worker主循环，实现自适应退避策略：
         * 1. 有任务且容量充足时：使用最小间隔(5ms)进行快速轮询，最大化吞吐量
         * 2. 无任务但容量充足时：采用指数退避策略，从5秒开始按1.5倍递增至最大5分钟，减少CPU消耗
         * 3. 容量不足时：固定等待60秒，避免无效轮询
         * 4. 异常情况时：同样采用指数退避，防止错误传播和资源浪费
         */
        @Override
        public void run() {
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                try {
                    ProcessResult result = processTask();

                    long waitTime;
                    if(result.hasCapacity) {
                        if(result.hasWork) {
                            backoff.onTaskFound();
                            waitTime = backoff.getMinInterval();
                        } else {
                            backoff.onTaskNotFound();
                            waitTime = backoff.getNextInterval();
                        }
                    } else {
                        waitTime = 60 * 1000;
                    }

                    Thread.sleep(waitTime);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Worker error for channel: {}", channel.getChannelCode(), e);
                    backoff.onTaskNotFound();
                    try {
                        Thread.sleep(backoff.getNextInterval());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        private ProcessResult processTask() {
            double capacity = capacityCalculator.getRemainingCapacity();
            boolean hasCapacity = capacity > 0.7;

            if(!hasCapacity) {
                return new ProcessResult(false, false);
            }

            boolean hasWork = false;
            int takedSize;
            do {
                String queue = String.format(QUEUE_NAME_TEMPLATE, channel.getQueueName());
                Take take = Take.builder()
                        .queues(Collections.singletonList(queue))
                        .size(Integer.valueOf(100))
                        .build();
                takedSize = worker.takeAndRun(take);
                if(takedSize > 0) {
                    hasWork = true;
                }
            } while (takedSize > 0);

            return new ProcessResult(hasCapacity, hasWork);
        }

        public void stop() {
            stopped = true;
        }

        public boolean isStopped() {
            return stopped;
        }

        private class ProcessResult {
            final boolean hasCapacity;
            final boolean hasWork;

            ProcessResult(boolean hasCapacity, boolean hasWork) {
                this.hasCapacity = hasCapacity;
                this.hasWork = hasWork;
            }
        }

        /**
         * 指数退避状态管理器，实现动态间隔调整策略：
         * - 成功时：立即重置为起始间隔，保持高响应性
         * - 失败时：按指数增长间隔，最大化系统稳定性
         * - 边界控制：最小5ms保证响应性，最大5分钟防止长时间阻塞
         */
        private class BackoffState {
            private final long MIN_INTERVAL = 5; // 最小间隔5ms
            private final long BACKOFF_START = 5000; // 退避起始间隔5秒
            private final long MAX_BACKOFF = 5 * 60 * 1000; // 最大退避间隔5分钟
            private final double BACKOFF_FACTOR = 1.5; // 退避倍数因子

            private long backoffInterval = BACKOFF_START;
            private long lastFailure = 0;

            long getMinInterval() {
                return MIN_INTERVAL;
            }

            long getNextInterval() {
                return backoffInterval;
            }

            void onTaskFound() {
                backoffInterval = BACKOFF_START;
                lastFailure = 0;
            }

            void onTaskNotFound() {
                lastFailure = System.currentTimeMillis();
                if(backoffInterval < MAX_BACKOFF) {
                    backoffInterval = Math.min(MAX_BACKOFF, (long) (backoffInterval * BACKOFF_FACTOR));
                }
            }
        }
    }
}
