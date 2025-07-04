package com.ke.bella.openapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.job.queue.worker.Task;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.db.repo.ChannelRepo;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class ModelAwareTaskHandler {
    
    @Autowired
    private ChannelIdleDetector channelIdleDetector;
    
    @Autowired
    private ChannelRepo channelRepository;
    
    @Autowired
    private ChannelRouter channelRouter;
    
    @Autowired
    private AdaptorManager adaptorManager;
    
    @Autowired
    private LimiterManager limiterManager;
    
    @Autowired
    private EndpointLogger endpointLogger;
    
    @Autowired
    private MetricsManager metricsManager;
    
    // 每个channel的并发任务计数
    private final ConcurrentHashMap<String, AtomicInteger> channelConcurrentTasks = new ConcurrentHashMap<>();
    
    /**
     * 处理任务，只在有空闲channel时执行
     */
    public void handleTask(Task task, String modelName) {
        try {
            // 1. 获取该模型的所有可用channels
            List<String> availableChannels = getAvailableChannelsForModel(modelName);
            
            if (availableChannels.isEmpty()) {
                log.debug("No available channels for model: {}, task will be retried later", modelName);
                task.retryLater();
                return;
            }
            
            // 2. 检查是否有空闲的channels
            List<String> idleChannels = channelIdleDetector.getIdleChannels(availableChannels);
            
            if (idleChannels.isEmpty()) {
                log.debug("No idle channels for model: {}, task will be retried later", modelName);
                task.retryLater();
                return;
            }
            
            // 3. 选择一个空闲channel执行任务
            String selectedChannel = selectBestChannel(idleChannels);
            
            // 4. 执行任务
            executeTask(task, modelName, selectedChannel);
            
        } catch (Exception e) {
            log.error("Failed to handle task for model: {}", modelName, e);
            task.fail("Task execution failed: " + e.getMessage());
        }
    }
    
    private List<String> getAvailableChannelsForModel(String modelName) {
        // 获取该模型对应的所有channel
        Condition.ChannelCondition condition = new Condition.ChannelCondition();
        condition.setEntityCode(modelName);
        condition.setStatus("active");
        
        List<ChannelDB> channels = channelRepository.list(condition);
        
        return channels.stream()
                .map(ChannelDB::getChannelCode)
                .collect(java.util.stream.Collectors.toList());
    }
    
    private String selectBestChannel(List<String> idleChannels) {
        // 简单策略：选择并发任务数最少的channel
        return idleChannels.stream()
                .min((c1, c2) -> {
                    int count1 = channelConcurrentTasks.getOrDefault(c1, new AtomicInteger(0)).get();
                    int count2 = channelConcurrentTasks.getOrDefault(c2, new AtomicInteger(0)).get();
                    return Integer.compare(count1, count2);
                })
                .orElse(idleChannels.get(0));
    }
    
    private void executeTask(Task task, String modelName, String channelCode) {
        AtomicInteger concurrentCount = channelConcurrentTasks.computeIfAbsent(channelCode, 
                k -> new AtomicInteger(0));
        
        concurrentCount.incrementAndGet();
        
        try {
            // 根据任务类型执行不同的处理逻辑
            String taskType = (String) task.getPayload().get("type");
            
            switch (taskType) {
                case "chat_completion":
                    executeChatCompletionTask(task, modelName, channelCode);
                    break;
                case "audio_transcription":
                    executeAudioTranscriptionTask(task, modelName, channelCode);
                    break;
                case "embedding":
                    executeEmbeddingTask(task, modelName, channelCode);
                    break;
                default:
                    log.warn("Unknown task type: {}", taskType);
                    task.fail("Unknown task type: " + taskType);
                    return;
            }
            
            task.succeed();
            log.info("Task executed successfully for model: {}, channel: {}", modelName, channelCode);
            
        } catch (Exception e) {
            log.error("Failed to execute task for model: {}, channel: {}", modelName, channelCode, e);
            task.fail("Execution failed: " + e.getMessage());
        } finally {
            concurrentCount.decrementAndGet();
        }
    }
    
    private void executeChatCompletionTask(Task task, String modelName, String channelCode) throws Exception {
        // 解析任务payload为CompletionRequest
        CompletionRequest request = JacksonUtils.deserialize(
                JacksonUtils.serialize(task.getPayload().get("request")), 
                CompletionRequest.class);
        
        String endpoint = "/v1/chat/completions";
        
        // 设置EndpointContext
        EndpointContext.setEndpointData(endpoint, modelName, request);
        
        // 获取指定的channel
        ChannelDB channel = channelRepository.getByUniqueKey(channelCode);
        
        if (channel == null) {
            throw new RuntimeException("Channel not found: " + channelCode);
        }
        
        EndpointContext.setEndpointData(channel);
        
        // 更新实时RPM
        channelIdleDetector.updateRealtimeRPM(EndpointContext.getProcessData());
        
        // 获取adaptor和property
        String protocol = EndpointContext.getProcessData().getProtocol();
        String url = EndpointContext.getProcessData().getForwardUrl();
        
        CompletionAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, CompletionAdaptor.class);
        CompletionProperty property = (CompletionProperty) JacksonUtils.deserialize(
                channel.getChannelInfo(), adaptor.getPropertyClass());
        
        // 执行completion
        CompletionResponse response = adaptor.completion(request, url, property);
        
        // 更新任务结果
        task.getPayload().put("response", JacksonUtils.deserialize(JacksonUtils.serialize(response), 
                new TypeReference<Object>() {}));
    }
    
    private void executeAudioTranscriptionTask(Task task, String modelName, String channelCode) throws Exception {
        // TODO: 实现音频转录任务处理
        log.info("Executing audio transcription task for model: {}, channel: {}", modelName, channelCode);
    }
    
    private void executeEmbeddingTask(Task task, String modelName, String channelCode) throws Exception {
        // TODO: 实现embedding任务处理
        log.info("Executing embedding task for model: {}, channel: {}", modelName, channelCode);
    }
}