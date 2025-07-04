package com.ke.bella.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.job.queue.worker.Task;
import com.ke.bella.job.queue.worker.TaskHandler;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于Channel感知的任务处理器
 * 只在有空闲channel时处理任务，集成现有的AdaptorManager和ChannelRouter
 */
@Slf4j
@Component
public class ChannelAwareTaskHandler implements TaskHandler {

    @Autowired
    private ChannelIdleDetector channelIdleDetector;

    @Autowired
    private ChannelRouter channelRouter;

    @Autowired
    private AdaptorManager adaptorManager;

    @Autowired
    private MetricsManager metricsManager;

    @Autowired
    private LimiterManager limiterManager;

    // 每个channel的并发任务计数
    private final AtomicInteger globalConcurrentTasks = new AtomicInteger(0);

    @Override
    public void execute(Task task) {
        String taskId = task.getTaskId();
        
        try {
            // 解析任务数据
            JsonNode taskData = task.getPayload(JsonNode.class);
            String endpoint = taskData.get("endpoint").asText();
            
            // 获取所有可用的channel
            List<String> availableChannels = getAllAvailableChannelCodes();
            
            // 检查是否有空闲的channel
            List<String> idleChannels = channelIdleDetector.getIdleChannels(availableChannels);
            
            if (idleChannels.isEmpty()) {
                log.debug("No idle channels available for task {}, retrying later", taskId);
                task.markRetryLater();
                return;
            }

            log.info("Processing task {} with {} idle channels available", taskId, idleChannels.size());
            
            // 执行任务
            executeTask(task, endpoint, idleChannels);
            
        } catch (Exception e) {
            log.error("Error executing task {}: {}", taskId, e.getMessage(), e);
            task.markFailed("Task execution failed: " + e.getMessage());
        }
    }

    private void executeTask(Task task, String endpoint, List<String> idleChannels) {
        String taskId = task.getTaskId();
        long startTime = System.currentTimeMillis();
        String selectedChannel = null;
        
        try {
            globalConcurrentTasks.incrementAndGet();
            
            // 解析请求数据
            JsonNode taskData = task.getPayload(JsonNode.class);
            JsonNode requestData = taskData.get("request");
            
            // 创建处理数据对象
            EndpointProcessData processData = new EndpointProcessData();
            processData.setEndpoint(endpoint);
            processData.setStartTime(startTime);
            processData.setTaskId(taskId);

            // 简单选择第一个空闲channel
            // TODO: 集成更复杂的路由逻辑
            selectedChannel = idleChannels.get(0);
            processData.setChannelCode(selectedChannel);
            
            log.debug("Task {} routed to channel {}", taskId, selectedChannel);

            // 执行请求处理
            OpenapiResponse response;
            if ("/v1/chat/completions".equals(endpoint)) {
                response = handleChatCompletion(processData, requestData);
            } else if ("/v1/audio/transcriptions".equals(endpoint)) {
                response = handleAudioTranscription(processData, requestData);
            } else if ("/v1/embeddings".equals(endpoint)) {
                response = handleEmbeddings(processData, requestData);
            } else {
                throw new RuntimeException("Unsupported endpoint: " + endpoint);
            }

            processData.setResponse(response);
            processData.setEndTime(System.currentTimeMillis());

            // 记录metrics
            metricsManager.record(processData);

            // 标记任务成功
            String result = JacksonUtils.serialize(response);
            task.markSucceed(result);
            
            log.info("Task {} completed successfully on channel {} in {}ms", 
                    taskId, selectedChannel, processData.getEndTime() - startTime);

        } catch (Exception e) {
            log.error("Task {} failed on channel {}: {}", taskId, selectedChannel, e.getMessage(), e);
            
            // 记录失败的metrics
            try {
                EndpointProcessData errorProcessData = new EndpointProcessData();
                errorProcessData.setEndpoint(endpoint);
                errorProcessData.setChannelCode(selectedChannel);
                errorProcessData.setStartTime(startTime);
                errorProcessData.setEndTime(System.currentTimeMillis());
                
                OpenapiResponse errorResponse = new OpenapiResponse();
                errorResponse.setError(new OpenapiResponse.Error(500, e.getMessage()));
                errorProcessData.setResponse(errorResponse);
                
                metricsManager.record(errorProcessData);
            } catch (Exception metricsError) {
                log.error("Failed to record error metrics: {}", metricsError.getMessage());
            }
            
            task.markFailed("Task execution failed: " + e.getMessage());
        } finally {
            globalConcurrentTasks.decrementAndGet();
        }
    }

    private OpenapiResponse handleChatCompletion(EndpointProcessData processData, JsonNode requestData) throws Exception {
        // 使用AdaptorManager处理chat completion请求
        return adaptorManager.execute(processData, requestData);
    }

    private OpenapiResponse handleAudioTranscription(EndpointProcessData processData, JsonNode requestData) throws Exception {
        // 使用AdaptorManager处理audio transcription请求
        return adaptorManager.execute(processData, requestData);
    }

    private OpenapiResponse handleEmbeddings(EndpointProcessData processData, JsonNode requestData) throws Exception {
        // 使用AdaptorManager处理embeddings请求
        return adaptorManager.execute(processData, requestData);
    }

    /**
     * 获取所有可用的channel codes
     * 这个方法需要根据实际的channel管理实现来完善
     */
    private List<String> getAllAvailableChannelCodes() {
        // TODO: 实现获取所有可用channel的逻辑
        // 这里应该从ChannelService或数据库中获取可用的channel列表
        // 暂时返回一个示例实现
        log.warn("getAllAvailableChannelCodes() is not fully implemented - using placeholder");
        return List.of("channel-1", "channel-2", "channel-3");
    }

    /**
     * 获取当前并发任务数
     */
    public int getConcurrentTaskCount() {
        return globalConcurrentTasks.get();
    }
}