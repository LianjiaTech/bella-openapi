package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.batch.BatchAdaptor;
import com.ke.bella.openapi.protocol.batch.BatchProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.WorkerMode;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.queue.Task;
import com.theokanning.openai.service.OpenAiService;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Builder
@SuppressWarnings("all")
public class PollBatchStatusWorker implements WorkerService {

    private final OpenAiService openAiService;
    private final AdaptorManager adaptorManager;

    private volatile PollTask pollTask;

    static final String CREATED_VENDOR_BATCH_QUEUE = "created_vendor_batch_queue";

    @Override
    public WorkerMode workerMode() {
        return WorkerMode.NONE;
    }

    @Override
    public String queueName() {
        return CREATED_VENDOR_BATCH_QUEUE;
    }

    @Override
    public void start() {
        pollTask = new PollTask(openAiService, adaptorManager);
        TaskExecutor.submit(pollTask);
    }

    @Override
    public void stop() {
        if(pollTask != null) {
            pollTask.stop();
        }
    }

    @Override
    public boolean isStopped() {
        return pollTask == null || pollTask.isStopped();
    }

    @Slf4j
    public static class PollTask implements Runnable {
        private static final long POLLING_INTERVAL = 5000;
        private static final int MAX_CONCURRENT_TASKS = 10;

        private final OpenAiService openAiService;
        private final AdaptorManager adaptorManager;

        @Getter
        private volatile boolean stopped = false;

        public PollTask(OpenAiService openAiService, AdaptorManager adaptorManager) {
            this.openAiService = openAiService;
            this.adaptorManager = adaptorManager;
        }

        @Override
        public void run() {
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                try {
                    boolean hasWork;
                    do {
                        hasWork = poll();
                    } while (hasWork && !stopped);

                    Thread.sleep(POLLING_INTERVAL);
                } catch (Exception e) {
                    log.error("Poll Batch Status error", e);
                    try {
                        Thread.sleep(POLLING_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        private boolean poll() {
            Take take = Take.builder()
                    .queues(Collections.singletonList(CREATED_VENDOR_BATCH_QUEUE + ":1"))
                    .size(10)
                    .processTimeout(60 * 5)
                    .processMaxRetries(-1)
                    .build();

            List<Task> tasks = openAiService.takeTasks(take)
                    .values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            if(tasks.isEmpty()) {
                return false;
            }

            List<CompletableFuture<Void>> futures = tasks.stream()
                    .map(task -> TaskExecutor.submit(() -> processTask(task)))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            return tasks.size() >= 100;
        }

        private void processTask(Task task) {
            try {
                Batch batch = resolve(task);
                if(!"validating".equals(batch.getStatus()) && !"in_progress".equals(batch.getStatus())) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("batch", batch);
                    completeTask(task.getTaskId(), result);
                }
            } catch (Exception e) {
                log.error("Error processing task: {}", task.getTaskId(), e);
            }
        }

        private Batch resolve(Task task) {
            Map<String, Object> data = task.getData();
            String batchId = MapUtils.getString(data, "batchId");
            String protocol = MapUtils.getString(data, "protocol");
            String channelUrl = MapUtils.getString(data, "channelUrl");
            String channelInfo = MapUtils.getString(data, "channelInfo");

            // 先获取adaptor，再使用adaptor.getPropertyClass()获取正确的Property子类
            BatchAdaptor<?> adaptor = adaptorManager.getProtocolAdaptor("/v1/batches"
                    , protocol, BatchAdaptor.class);
            BatchProperty batchProperty = (BatchProperty) JacksonUtils.deserialize(
                    channelInfo, adaptor.getPropertyClass());

            @SuppressWarnings("unchecked")
            BatchAdaptor<BatchProperty> typedAdaptor = (BatchAdaptor<BatchProperty>) adaptor;

            Batch batch = typedAdaptor.retrieveBatch(batchId, channelUrl, batchProperty);
            log.info("Retrieved batch status, batchId: {}, status: {}", batchId, batch.getStatus());
            if("completed".equals(batch.getStatus()) && StringUtils.isNotBlank(batch.getOutputFileId())) {
                List<String> results = typedAdaptor.downloadTasks(batch.getOutputFileId(), batchProperty);
                results.forEach(result -> {
                    Map resultMap = JacksonUtils.deserialize(result, Map.class);
                    Map response = MapUtils.getMap(resultMap, "response");
                    String taskId = MapUtils.getString(resultMap, "custom_id");
                    response.put("request_id", taskId);
                    completeTask(taskId, response);
                });
            }

            return batch;
        }

        private void completeTask(String taskId, Map<String, Object> result) {
            try {
                openAiService.completeTask(taskId, result);
            } catch (Exception e) {
                log.error("Failed to complete task, taskId: {}", taskId, e);
            }
        }

        public void stop() {
            stopped = true;
        }
    }
}
