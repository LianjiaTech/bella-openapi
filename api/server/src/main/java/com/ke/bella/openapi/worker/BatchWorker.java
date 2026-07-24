package com.ke.bella.openapi.worker;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategy;
import com.google.common.collect.Maps;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.batch.BatchAdaptor;
import com.ke.bella.openapi.protocol.batch.BatchProperty;
import com.ke.bella.openapi.protocol.batch.BatchRetriableException;
import com.ke.bella.openapi.protocol.limiter.ConcurrentPermitLimiter;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.QueueMode;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Queue;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.queue.Task;
import com.theokanning.openai.service.OpenAiService;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.worker.PollBatchStatusWorker.CREATED_VENDOR_BATCH_QUEUE;

@Slf4j
@Builder
@SuppressWarnings("all")
public class BatchWorker implements WorkerService {

    private final ChannelDB channel;
    private final OpenAiService openAiService;
    private final Queue queue;
    private final AdaptorManager adaptorManager;
    private final ConcurrentPermitLimiter concurrentPermitLimiter;

    private volatile CreateBatchTask createBatchTask;

    @Override
    public QueueMode workerMode() {
        return QueueMode.BATCH;
    }

    @Override
    public String queueName() {
        return channel.getQueueName();
    }

    @Override
    public void start() {
        createBatchTask = new CreateBatchTask(openAiService, adaptorManager, channel, queue, concurrentPermitLimiter);
        TaskExecutor.submit(createBatchTask);
    }

    @Override
    public void stop() {
        if(createBatchTask != null) {
            createBatchTask.stop();
        }
    }

    @Override
    public boolean isStopped() {
        return createBatchTask == null || createBatchTask.isStopped();
    }

    @Slf4j
    public static class CreateBatchTask implements Runnable {
        private static final String OPERATION_BATCH_PROCESS = "batch_process";
        private static final String PERMIT_KEY_FORMAT = "batch:rate:%s:%s";
        private static final long POLLING_INTERVAL = 5000;
        private static final int MAX_UPLOAD_BACKOFF_SECONDS = 30;
        private static final int BATCH_PROCESS_MAX_PERMITS = 1;
        private static final int PERMIT_TTL_SECONDS = 10 * 60;
        private static final int DEFAULT_UPLOAD_MAX_RETRIES = 10;
        private static final int DEFAULT_COMPLETION_WINDOW_DAYS = 7;
        static final String DEFAULT_COMPLETION_WINDOW = DEFAULT_COMPLETION_WINDOW_DAYS + "d";
        static final int DEFAULT_COMPLETION_WINDOW_TIMEOUT_SECONDS = DEFAULT_COMPLETION_WINDOW_DAYS * 24 * 60 * 60;

        private final ChannelDB channel;
        private final OpenAiService openAiService;
        private final AdaptorManager adaptorManager;
        private final Queue queue;
        private final ConcurrentPermitLimiter concurrentPermitLimiter;

        @Getter
        private volatile boolean stopped = false;

        public CreateBatchTask(OpenAiService openAiService, AdaptorManager adaptorManager, ChannelDB channel, Queue queue,
                ConcurrentPermitLimiter concurrentPermitLimiter) {
            this.openAiService = openAiService;
            this.adaptorManager = adaptorManager;
            this.channel = channel;
            this.queue = queue;
            this.concurrentPermitLimiter = concurrentPermitLimiter;
        }

        @Override
        public void run() {
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                try {
                    boolean hasTask;
                    do {
                        hasTask = processTask();
                    } while (hasTask && !stopped);

                    Thread.sleep(POLLING_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Batch WorkerService error for channel: {}", channel.getChannelCode(), e);
                    try {
                        Thread.sleep(POLLING_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        private boolean processTask() {
            BatchAdaptor<?> adaptor = adaptorManager.getProtocolAdaptor("/v1/batches"
                    , channel.getProtocol(), BatchAdaptor.class);
            BatchProperty batchProperty = (BatchProperty) JacksonUtils.deserialize(
                    channel.getChannelInfo(), adaptor.getPropertyClass());

            int maxSize = batchProperty.getMaxSize();
            if(maxSize <= 0) {
                maxSize = 500;
            }
            String channelCode = channel.getChannelCode();
            String permitKey = String.format(PERMIT_KEY_FORMAT, channelCode, OPERATION_BATCH_PROCESS);
            String permitId = concurrentPermitLimiter.tryAcquire(permitKey, BATCH_PROCESS_MAX_PERMITS, PERMIT_TTL_SECONDS);

            if(permitId == null) {
                log.info("Batch process permit unavailable: channelCode={}, operation={}, maxPermits={}",
                        channelCode, OPERATION_BATCH_PROCESS, BATCH_PROCESS_MAX_PERMITS);
                return false;
            }

            try {
                Take take = Take.builder()
                        .queues(Collections.singletonList(channel.getQueueName() + ":1"))
                        .size(maxSize)
                        .strategy("active_passive")
                        .build();

                List<Task> tasks = openAiService.takeTasks(take).values().stream()
                        .flatMap(List::stream).collect(Collectors.toList());
                if(CollectionUtils.isEmpty(tasks)) {
                    return false;
                }

                BatchAdaptor<BatchProperty> typedAdaptor = (BatchAdaptor<BatchProperty>) adaptor;
                String fileId = uploadTasksWithRetry(typedAdaptor, tasks, batchProperty);
                log.info("Uploaded {} tasks to file service, fileId: {}", tasks.size(), fileId);

                BatchRequest batchRequest = BatchRequest.builder()
                        .inputFileId(fileId)
                        .endpoint(queue.getEndpoint())
                        .completionWindow(DEFAULT_COMPLETION_WINDOW)
                        .build();
                Batch batch = typedAdaptor.createBatch(batchRequest, channel.getUrl(), batchProperty);
                log.info("Created batch successfully, batchId: {}, status: {}", batch.getId(), batch.getStatus());

                Map<String, Object> data = Maps.newHashMap();
                data.put("batchId", batch.getId());
                data.put("batch", batch);
                data.put("protocol", channel.getProtocol());
                data.put("channelUrl", channel.getUrl());
                data.put("channelInfo", channel.getChannelInfo());
                Put put = Put.builder().queue(CREATED_VENDOR_BATCH_QUEUE)
                        .level(1)
                        .endpoint("/v1/batches")
                        .data(data)
                        .timeout(DEFAULT_COMPLETION_WINDOW_TIMEOUT_SECONDS)
                        .build();
                openAiService.putTask(put);
                return tasks.size() >= maxSize;
            } finally {
                if(StringUtils.isNotBlank(permitId)) {
                    concurrentPermitLimiter.tryRelease(permitKey, permitId);
                }
            }
        }

        private String uploadTasksWithRetry(BatchAdaptor<BatchProperty> adaptor, List<Task> tasks, BatchProperty batchProperty) {
            int maxAttempts = DEFAULT_UPLOAD_MAX_RETRIES + 1;
            Retryer<String> retryer = RetryerBuilder.<String>newBuilder()
                    .retryIfExceptionOfType(BatchRetriableException.class)
                    .withWaitStrategy(createUploadWaitStrategy())
                    .withStopStrategy(StopStrategies.stopAfterAttempt(maxAttempts))
                    .build();

            try {
                return retryer.call(() -> adaptor.uploadTasks(tasks, batchProperty));
            } catch (RetryException | ExecutionException e) {
                log.warn("Failed to upload batch file, retrying...", e);
                throw new RuntimeException("Failed to upload batch file");
            }
        }

        private WaitStrategy createUploadWaitStrategy() {
            Random random = new Random();
            return attempt -> {
                long attemptNumber = Math.max(1, attempt.getAttemptNumber());
                long baseDelaySeconds = Math.min(1L << Math.min(attemptNumber - 1, 30), MAX_UPLOAD_BACKOFF_SECONDS);
                double jitterFactor = 0.75 + random.nextDouble() * 0.5;
                return (long) (TimeUnit.SECONDS.toMillis(baseDelaySeconds) * jitterFactor);
            };
        }

        public void stop() {
            stopped = true;
        }
    }
}
