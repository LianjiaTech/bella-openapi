package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.limiter.ConcurrentPermitLimiter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.openapi.server.OpenapiProperties;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.QueueMode;
import com.theokanning.openai.queue.Queue;
import com.theokanning.openai.service.OpenAiService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnProperty(name = "bella.openapi.as-worker.enabled", havingValue = "true")
public class WorkerManager {
    private static final String SINGLE_WORKER_VM_ENABLED_PROPERTY = "bella.openapi.as-worker.single-vm-enabled";
    private static final String BATCH_WORKER_VM_ENABLED_PROPERTY = "bella.openapi.as-worker.batch-vm-enabled";

    @Resource
    private ChannelService channelService;
    @Resource
    private AdaptorManager adaptorManager;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private OpenAiServiceFactory openAiServiceFactory;
    @Resource
    private OpenapiProperties openapiProperties;
    @Resource
    private OpenapiClient openapiClient;
    @Resource
    private LuaScriptExecutor luaScriptExecutor;
    @Resource
    private LimiterManager limiterManager;
    @Resource
    private ConcurrentPermitLimiter concurrentPermitLimiter;
    @Resource
    private ISafetyCheckService<SafetyCheckRequest.Chat> chatSafetyCheckService;

    @Value("${bella.openapi.as-worker.remaining-capacity-threshold:0.7}")
    @Getter
    private double remainingCapacityThreshold;

    @Value("${bella.openapi.as-worker.min-age-seconds:1}")
    @Getter
    private long minAgeSeconds;

    @Value("${bella.openapi.as-worker.max-concurrency:100}")
    @Getter
    private int maxConcurrency;

    @Value("${bella.openapi.as-worker.batch-enabled:false}")
    private boolean batchWorkerEnabled;

    @Value("${bella.openapi.as-worker.poll-batch-status-enabled:true}")
    private boolean pollBatchStatusEnabled;

    @Value("${bella.openapi.as-worker.video-enabled:false}")
    @Getter
    private boolean videoWorkerEnabled;

    private OpenAiService openAiService;

    private final Map<String, WorkerService> runningWorkers = new ConcurrentHashMap<>();

    private PollBatchStatusWorker pollBatchStatusWorker;

    @PostConstruct
    public void init() {
        openAiService = openAiServiceFactory.create(openapiProperties.getServiceAk());
        TaskExecutor.scheduleAtFixedRate(() -> {
            try {
                refreshWorkers();
            } catch (Exception e) {
                log.error("Failed to refresh workers", e);
            }
        }, 60 * 2);

        if (pollBatchStatusEnabled) {
            pollBatchStatusWorker = PollBatchStatusWorker.builder()
                    .openAiService(openAiService)
                    .adaptorManager(adaptorManager)
                    .build();
            pollBatchStatusWorker.start();
            log.info("Started PollBatchStatusWorker");
        } else {
            log.info("PollBatchStatusWorker is disabled by configuration");
        }
    }

    private void refreshWorkers() {
        List<ChannelDB> channels = channelService.listAllWorkerChannels();
        Map<String, ChannelDB> channelMap = channels.stream()
                .filter(channel -> {
                    QueueMode mode = QueueMode.of(channel.getQueueMode()).toWorkerMode();
                    if(mode == QueueMode.NONE) {
                        log.warn("Channel {} has unsupported queueMode for worker, skipping", channel.getChannelCode());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toMap(
                        channel -> buildWorkerKey(channel, QueueMode.of(channel.getQueueMode()).toWorkerMode()),
                        Function.identity(),
                        (a, b) -> b
                ));

        synchronized(runningWorkers) {
            runningWorkers.keySet().removeIf(channelCode -> {
                WorkerService worker = runningWorkers.get(channelCode);
                ChannelDB channel = channelMap.get(channelCode);

                if(channel == null) {
                    log.info("Channel removed from database, stopping worker: {}", channelCode);
                    worker.stop();
                    return true;
                }

                boolean queueChanged = !Objects.equals(worker.queueName(), channel.getQueueName());
                if(queueChanged) {
                    log.info("Configuration changed for worker: {}, stopping and will restart", channelCode);
                    worker.stop();
                    return true;
                }
                return false;
            });

            for (ChannelDB channel : channelMap.values()) {
                QueueMode mode = QueueMode.of(channel.getQueueMode()).toWorkerMode();

                if(mode == QueueMode.SINGLE && isSingleWorkerVmEnabled()) {
                    startWorker(channel, QueueMode.SINGLE);
                }

                if(mode == QueueMode.BATCH && batchWorkerEnabled && isBatchWorkerVmEnabled()) {
                    startWorker(channel, QueueMode.BATCH);
                }
            }
        }
    }

    private boolean isBatchWorkerVmEnabled() {
        return Boolean.parseBoolean(System.getProperty(BATCH_WORKER_VM_ENABLED_PROPERTY, "true"));
    }

    private boolean isSingleWorkerVmEnabled() {
        return Boolean.parseBoolean(System.getProperty(SINGLE_WORKER_VM_ENABLED_PROPERTY, "true"));
    }

    private void startWorker(ChannelDB channel, QueueMode mode) {
        String workerKey = buildWorkerKey(channel, mode == QueueMode.BATCH ? QueueMode.BATCH : QueueMode.SINGLE);
        if(runningWorkers.containsKey(workerKey)) {
            return;
        }

        Queue queue = openAiService.getQueue(channel.getQueueName());
        if(queue == null) {
            log.warn("Queue not found for channel: {}, queueName: {}, skipping worker start"
                    , channel.getChannelCode(), channel.getQueueName());
            return;
        }

        WorkerService worker = null;
        if(mode == QueueMode.SINGLE) {
            worker = SingleWorker.builder()
                    .channel(channel)
                    .redissonClient(redissonClient)
                    .openAiService(openAiService)
                    .openapiClient(openapiClient)
                    .adaptorManager(adaptorManager)
                    .luaScriptExecutor(luaScriptExecutor)
                    .limiterManager(limiterManager)
                    .chatSafetyCheckService(chatSafetyCheckService)
                    .workerManager(this)
                    .build();
        } else if(mode == QueueMode.BATCH) {
            worker = BatchWorker.builder()
                    .channel(channel)
                    .openAiService(openAiService)
                    .queue(queue)
                    .adaptorManager(adaptorManager)
                    .concurrentPermitLimiter(concurrentPermitLimiter)
                    .build();
        }

        if(worker != null) {
            worker.start();
            runningWorkers.put(workerKey, worker);
            log.info("Started {} worker for channel: {}", mode, channel.getChannelCode());
        }
    }

    private String buildWorkerKey(ChannelDB channel, QueueMode mode) {
        return channel.getChannelCode() + "_" + mode.getCode();
    }

    @PreDestroy
    public void destroy() {
        if(pollBatchStatusWorker != null) {
            pollBatchStatusWorker.stop();
            log.info("Stopped PollBatchStatusWorker");
        }

        if(runningWorkers.isEmpty()) {
            return;
        }

        log.info("Stopping {} workers...", runningWorkers.size());
        runningWorkers.values().forEach(WorkerService::stop);

        for (int i = 0; i < 30; i++) {
            boolean allStopped = runningWorkers.values().stream().allMatch(WorkerService::isStopped);
            boolean pollWorkerStopped = pollBatchStatusWorker == null || pollBatchStatusWorker.isStopped();

            if(allStopped && pollWorkerStopped) {
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

}
