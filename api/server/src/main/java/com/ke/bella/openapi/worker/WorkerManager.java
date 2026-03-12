package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.openapi.server.OpenapiProperties;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.WorkerMode;
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

    @Value("${bella.openapi.as-worker.remaining-capacity-threshold:0.7}")
    @Getter
    private double remainingCapacityThreshold;

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
        }, 3 * 10);

        pollBatchStatusWorker = PollBatchStatusWorker.builder()
                .openAiService(openAiService)
                .adaptorManager(adaptorManager)
                .build();
        pollBatchStatusWorker.start();
        log.info("Started PollBatchStatusWorker");
    }

    private void refreshWorkers() {
        List<ChannelDB> channels = channelService.listAllWorkerChannels();
        Map<String, ChannelDB> channelMap = channels.stream()
                .collect(Collectors.toMap(
                        channel -> buildWorkerKey(channel, WorkerMode.of(channel.getWorkerMode())),
                        Function.identity()
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
                boolean modeChanged = !Objects.equals(worker.workerMode().getCode(), channel.getWorkerMode().intValue());
                if(queueChanged || modeChanged) {
                    log.info("Configuration changed for worker: {}, stopping and will restart", channelCode);
                    worker.stop();
                    return true;
                }
                return false;
            });

            for (ChannelDB channel : channels) {
                try {
                    WorkerMode mode = WorkerMode.of(channel.getWorkerMode());
                    if(mode.supportsSingle()) {
                        startWorker(channel, WorkerMode.SINGLE);
                    } else if(mode.supportsBatch()) {
                        startWorker(channel, WorkerMode.BATCH);
                    }
                } catch (Exception e) {
                    log.error("Failed to start worker for channel: {}", channel.getChannelCode(), e);
                }
            }
        }
    }

    private void startWorker(ChannelDB channel, WorkerMode mode) {
        String workerKey = buildWorkerKey(channel, mode);
        if(runningWorkers.containsKey(workerKey)) {
            return;
        }

        Queue queue = openAiService.getQueue(channel.getQueueName());
        if(queue == null) {
            log.warn("Queue not found for channel: {}, queueName: {}, skip starting worker"
                    , channel.getChannelCode(), channel.getQueueName());
            return;
        }

        WorkerService worker = null;
        if(mode == WorkerMode.SINGLE) {
            worker = SingleWorker.builder()
                    .channel(channel)
                    .redissonClient(redissonClient)
                    .openAiService(openAiService)
                    .openapiClient(openapiClient)
                    .adaptorManager(adaptorManager)
                    .luaScriptExecutor(luaScriptExecutor)
                    .limiterManager(limiterManager)
                    .workerManager(this)
                    .build();
        } else if(mode == WorkerMode.BATCH) {
            worker = BatchWorker.builder()
                    .channel(channel)
                    .queue(queue)
                    .openAiService(openAiService)
                    .adaptorManager(adaptorManager)
                    .build();
        }

        if(worker != null) {
            worker.start();
            runningWorkers.put(workerKey, worker);
            log.info("Started {} worker for channel: {}", mode, channel.getChannelCode());
        }
    }

    private String buildWorkerKey(ChannelDB channel, WorkerMode mode) {
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
