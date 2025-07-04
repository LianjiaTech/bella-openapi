package com.ke.bella.openapi.service;

import com.ke.bella.job.queue.worker.Task;
import com.ke.bella.job.queue.worker.TaskHandler;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Channel感知的任务处理器
 * 只在有空闲channel时处理任务，集成现有的协议适配器系统
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

    @Override
    public void execute(Task task) {
        String taskId = task.getTaskId();
        log.info("Processing task: {}", taskId);

        try {
            // 1. 检查是否有空闲的channels
            List<String> idleChannels = channelIdleDetector.getIdleChannels();
            if (idleChannels.isEmpty()) {
                log.debug("No idle channels available, retrying task {} later", taskId);
                task.markRetryLater();
                return;
            }

            // 2. 根据任务类型处理不同的请求
            String endpoint = extractEndpointFromTask(task);
            Object result = processTaskByEndpoint(task, endpoint, idleChannels);

            // 3. 标记任务成功
            String resultJson = JacksonUtils.serialize(result);
            task.markSucceed(resultJson);
            
            log.info("Successfully processed task: {}", taskId);

        } catch (Exception e) {
            log.error("Failed to process task: {}", taskId, e);
            task.markFailed("Error: " + e.getMessage());
        }
    }

    /**
     * 从任务中提取endpoint信息
     */
    private String extractEndpointFromTask(Task task) {
        // 根据task的数据结构提取endpoint
        // 这里需要根据实际的task payload结构来实现
        try {
            // 假设task payload包含endpoint信息
            return "/v1/chat/completions"; // 默认使用chat completion
        } catch (Exception e) {
            log.warn("Failed to extract endpoint from task, using default", e);
            return "/v1/chat/completions";
        }
    }

    /**
     * 根据endpoint类型处理不同的任务
     */
    private Object processTaskByEndpoint(Task task, String endpoint, List<String> availableChannels) {
        switch (endpoint) {
            case "/v1/chat/completions":
                return processChatCompletionTask(task, availableChannels);
            
            case "/v1/audio/transcriptions":
                return processAudioTranscriptionTask(task, availableChannels);
            
            case "/v1/embeddings":
                return processEmbeddingTask(task, availableChannels);
            
            default:
                throw new IllegalArgumentException("Unsupported endpoint: " + endpoint);
        }
    }

    /**
     * 处理聊天完成任务
     */
    private Object processChatCompletionTask(Task task, List<String> availableChannels) {
        try {
            // 1. 解析任务payload为CompletionRequest
            CompletionRequest request = task.getPayload(CompletionRequest.class);
            
            // 2. 创建处理上下文
            EndpointProcessData processData = createProcessData(task, "/v1/chat/completions");
            
            // 3. 选择最佳的空闲channel
            // 这里简化实现，选择第一个可用的channel
            // 实际可以根据负载均衡策略选择
            String selectedChannel = availableChannels.get(0);
            log.debug("Selected channel {} for task {}", selectedChannel, task.getTaskId());
            
            // 4. 使用现有的协议适配器处理请求
            // TODO: 集成ChannelRouter选择具体的adaptor
            // 这里需要根据selectedChannel获取对应的adaptor并处理请求
            
            // 暂时返回模拟响应
            CompletionResponse response = new CompletionResponse();
            response.setModel(request.getModel());
            return response;
            
        } catch (Exception e) {
            log.error("Failed to process chat completion task", e);
            throw e;
        }
    }

    /**
     * 处理音频转录任务
     */
    private Object processAudioTranscriptionTask(Task task, List<String> availableChannels) {
        try {
            log.debug("Processing audio transcription task with {} available channels", availableChannels.size());
            
            // TODO: 实现音频转录任务处理逻辑
            // 1. 解析音频转录请求
            // 2. 选择支持音频转录的channel
            // 3. 调用对应的协议适配器
            
            // 暂时返回模拟响应
            return "Audio transcription completed for task: " + task.getTaskId();
            
        } catch (Exception e) {
            log.error("Failed to process audio transcription task", e);
            throw e;
        }
    }

    /**
     * 处理嵌入任务
     */
    private Object processEmbeddingTask(Task task, List<String> availableChannels) {
        try {
            log.debug("Processing embedding task with {} available channels", availableChannels.size());
            
            // TODO: 实现嵌入任务处理逻辑
            // 1. 解析嵌入请求
            // 2. 选择支持嵌入的channel
            // 3. 调用对应的协议适配器
            
            // 暂时返回模拟响应
            return "Embedding completed for task: " + task.getTaskId();
            
        } catch (Exception e) {
            log.error("Failed to process embedding task", e);
            throw e;
        }
    }

    /**
     * 创建处理上下文数据
     */
    private EndpointProcessData createProcessData(Task task, String endpoint) {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint(endpoint);
        processData.setApikey(task.getAk());
        // TODO: 设置其他必要的处理数据
        return processData;
    }
}