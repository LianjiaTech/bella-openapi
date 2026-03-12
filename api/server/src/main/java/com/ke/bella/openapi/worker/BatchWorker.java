package com.ke.bella.openapi.worker;

import com.google.common.collect.Maps;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.batch.BatchAdaptor;
import com.ke.bella.openapi.protocol.batch.BatchProperty;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.WorkerMode;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.queue.Task;
import com.theokanning.openai.service.OpenAiService;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.worker.PollBatchStatusWorker.CREATED_VENDOR_BATCH_QUEUE;

@Slf4j
@Builder
@SuppressWarnings("all")
public class BatchWorker implements WorkerService {

    private final ChannelDB channel;
    private final OpenAiService openAiService;

    private final AdaptorManager adaptorManager;

    private volatile CreateBatchTask createBatchTask;

    @Override
    public WorkerMode workerMode() {
        return WorkerMode.BATCH;
    }

    @Override
    public String queueName() {
        return channel.getQueueName();
    }

    @Override
    public void start() {
        createBatchTask = new CreateBatchTask(openAiService, adaptorManager, channel);
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
        private static final long POLLING_INTERVAL = 5000;

        private final ChannelDB channel;
        private final OpenAiService openAiService;
        private final AdaptorManager adaptorManager;

        @Getter
        private volatile boolean stopped = false;

        public CreateBatchTask(OpenAiService openAiService, AdaptorManager adaptorManager, ChannelDB channel) {
            this.openAiService = openAiService;
            this.adaptorManager = adaptorManager;
            this.channel = channel;
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
            String fileId = typedAdaptor.uploadTasks(tasks, batchProperty);
            log.info("Uploaded {} tasks to file service, fileId: {}", tasks.size(), fileId);

            BatchRequest batchRequest = BatchRequest.builder()
                    .inputFileId(fileId)
                    .endpoint("/v1/chat/completions")
                    .completionWindow("24h")
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
                    .timeout(24 * 60 * 60)
                    .build();
            openAiService.putTask(put);
            return tasks.size() >= maxSize;
        }

        public void stop() {
            stopped = true;
        }
    }
}
