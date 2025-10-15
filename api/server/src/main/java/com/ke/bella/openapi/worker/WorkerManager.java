package com.ke.bella.openapi.worker;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.endpoints.ChatController;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.TaskWrapper;
import com.ke.bella.queue.worker.Worker;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnProperty(name = "bella.openapi.as-worker.enabled", havingValue = "true")
public class WorkerManager {

    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    @Resource
    private ChannelService channelService;

    @Resource
    private ChatController chatController;

    @Resource
    private ChannelIdleDetector channelIdleDetector;

    @Resource
    private MetricsManager metricsManager;

    @Value("${bella.openapi.api-key}")
    private String openApiKey;
    @Value("${bella.openapi.base-url}")
    private String openApiBase;

    private OpenAiService openAiService;
    private final Map<String, WorkerContext> runningWorkers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        openAiService = new OpenAiService(openApiKey, openApiBase);
        refreshWorkers();
        TaskExecutor.scheduleAtFixedRate(this::refreshWorkers, 60 * 10);
    }

    private void refreshWorkers() {
        List<ChannelDB> channels = channelService.listAllWorkerChannels();
        Map<String, String> channelMap = channels.stream()
                .collect(Collectors.toMap(ChannelDB::getChannelCode, ChannelDB::getQueueName));

        synchronized(runningWorkers) {
            runningWorkers.keySet().removeIf(channelCode -> {
                WorkerContext context = runningWorkers.get(channelCode);
                String queueName = channelMap.get(channelCode);

                if(queueName == null || !context.isSameQueue(queueName)) {
                    context.stop();
                    return true;
                } else {
                    return false;
                }
            });

            channels.stream().filter(channel -> !runningWorkers.containsKey(channel.getChannelCode()))
                    .forEach(this::startWorker);
        }
    }

    private void startWorker(ChannelDB channel) {
        Worker worker = new Worker(task -> {
            try {
                executeTask(task, channel);
            } catch (Exception e) {
                task.markComplete(createErrorResult(e.getMessage()));
            }
        }, openAiService);

        Take take = Take.builder().queues(Lists.newArrayList(channel.getQueueName() + ":1")).size(1).build();
        ScheduledFuture<?> future = TaskExecutor.scheduleAtFixedRate(() -> {
            try {
                int takedSize;
                do {
                    if(channelIdleDetector.hasEnoughCapacity(channel.getChannelCode())) {
                        takedSize = worker.takeAndRun(take);
                    } else {
                        takedSize = 0;
                    }
                } while (takedSize > 0);
            } catch (Exception e) {
                log.error("Worker scheduling error for channel: {}", channel.getChannelCode(), e);
            }
        }, 5);

        runningWorkers.put(channel.getChannelCode(), new WorkerContext(channel.getQueueName(), future));
    }

    private void executeTask(TaskWrapper taskWrapper, ChannelDB channel) {
        OpenapiResponse response;
        if(EntityConstants.MODEL.equals(channel.getEntityType())) {
            response = chatController.processCompletionRequest(CHAT_COMPLETIONS_ENDPOINT, taskWrapper.getTask().getData(), channel);
        } else {
            throw new UnsupportedOperationException("Unsupported entity type: " + channel.getEntityType());
        }

        int httpCode = response.getError() == null ? 200
                : Optional.ofNullable(response.getError().getHttpCode()).orElse(500);

        if(httpCode == 200) {
            Map<String, Object> result = new HashMap<>();
            result.put("status_code", 200);
            result.put("body", response);
            taskWrapper.markComplete(result);
        } else if(httpCode == 429 || httpCode == 503) {
            recordChannelUnavailable(channel, response);
            taskWrapper.markRetryLater();
        } else {
            taskWrapper.markComplete(createErrorResult(response.getError().getMessage()));
        }
    }

    private void recordChannelUnavailable(ChannelDB channel, OpenapiResponse response) {
        try {
            EndpointProcessData processData = new EndpointProcessData();
            String endpoint = EntityConstants.MODEL.equals(channel.getEntityType())
                    ? CHAT_COMPLETIONS_ENDPOINT
                    : channel.getEntityCode();
            processData.setSupplier(channel.getSupplier());
            processData.setEndpoint(endpoint);
            processData.setChannelCode(channel.getChannelCode());
            processData.setResponse(response);
            processData.setModel(channel.getEntityCode());
            metricsManager.record(processData);
        } catch (Exception e) {
            log.warn("Failed to record service unavailable for channel: {}", channel.getChannelCode(), e);
        }
    }

    private Map<String, Object> createErrorResult(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", errorMessage);
        result.put("status_code", 500);
        result.put("body", errorBody);
        return result;
    }

    @PreDestroy
    public void destroy() {
        if(runningWorkers.isEmpty()) {
            return;
        }

        log.info("Stopping {} workers...", runningWorkers.size());
        runningWorkers.values().forEach(WorkerContext::stop);

        for (int i = 0; i < 30; i++) {
            if(runningWorkers.values().stream().allMatch(WorkerContext::isStopped)) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        runningWorkers.clear();
        log.info("WorkerManager destroyed");
    }

    static class WorkerContext {
        private final String queueName;
        private final ScheduledFuture<?> future;

        public WorkerContext(String queueName, ScheduledFuture<?> future) {
            this.queueName = queueName;
            this.future = future;
        }

        public void stop() {
            if(future != null) {
                future.cancel(false);
            }
        }

        public boolean isStopped() {
            return future == null || future.isCancelled() || future.isDone();
        }

        public boolean isSameQueue(String queueName) {
            return Objects.equals(this.queueName, queueName);
        }
    }
}
