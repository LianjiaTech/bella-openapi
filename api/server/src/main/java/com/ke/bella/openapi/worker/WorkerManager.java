package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.openapi.server.OpenapiProperties;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private OpenAiService openAiService;

    private final Map<String, WorkerContext> runningWorkers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        openAiService = openAiServiceFactory.create(openapiProperties.getServiceAk());
        TaskExecutor.scheduleAtFixedRate(() -> {
            try {
                refreshWorkers();
            } catch (Exception e) {
                log.error("Failed to refresh workers", e);
            }
        }, 60 * 10);
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

            for (ChannelDB channel : channels) {
                if(!runningWorkers.containsKey(channel.getChannelCode())) {
                    WorkerContext workerContext = WorkerContext.builder()
                            .channel(channel)
                            .redissonClient(redissonClient)
                            .openAiService(openAiService)
                            .openapiClient(openapiClient)
                            .adaptorManager(adaptorManager)
                            .luaScriptExecutor(luaScriptExecutor)
                            .build();
                    workerContext.start();
                    runningWorkers.put(channel.getChannelCode(), workerContext);
                }
            }
        }
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

}
