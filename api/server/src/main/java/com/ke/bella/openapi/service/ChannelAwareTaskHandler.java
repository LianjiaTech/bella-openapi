package com.ke.bella.openapi.service;

import com.ke.bella.job.queue.worker.Task;
import com.ke.bella.job.queue.worker.TaskHandler;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.log.MetricsLogHandler;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 渠道感知的任务处理器
 * 
 * 集成现有的AdaptorManager和ChannelRouter系统
 * 支持多种endpoint任务处理
 */
@Slf4j
@Component
public class ChannelAwareTaskHandler implements TaskHandler {

    @Autowired
    private ChannelService channelService;
    
    @Autowired
    private AdaptorManager adaptorManager;
    
    @Autowired 
    private ChannelRouter channelRouter;
    
    @Autowired
    private MetricsManager metricsManager;
    
    @Autowired
    private MetricsLogHandler metricsLogHandler;
    
    @Override
    public void execute(Task task) {
        String taskId = task.getTaskId();
        String ak = task.getAk();
        
        log.info("开始处理JobQueue任务，taskId={}, ak={}", taskId, ak);
        
        try {
            // 从任务payload中获取请求信息
            JobTaskPayload payload = task.getPayload(JobTaskPayload.class);
            if (payload == null) {
                log.error("任务{}的payload为空", taskId);
                task.markFailed("payload为空");
                return;
            }
            
            String channelCode = payload.getChannelCode();
            String endpoint = payload.getEndpoint();
            
            // 验证渠道是否存在且可用
            ChannelDB channel = channelService.getActiveByChannelCode(channelCode);
            if (channel == null) {
                log.warn("任务{}指定的渠道{}不存在或不可用", taskId, channelCode);
                task.markRetryLater();
                return;
            }
            
            // 检查渠道是否空闲（这里可以集成之前的空闲检测逻辑）
            if (!isChannelIdle(channelCode)) {
                log.info("渠道{}当前不空闲，任务{}稍后重试", channelCode, taskId);
                task.markRetryLater();
                return;
            }
            
            // 处理不同类型的任务
            String result = processTaskByEndpoint(endpoint, payload, channel);
            
            // 标记任务成功
            task.markSucceed(result);
            log.info("任务{}处理成功", taskId);
            
        } catch (Exception e) {
            log.error("处理任务{}时发生异常", taskId, e);
            task.markFailed("处理异常: " + e.getMessage());
        }
    }
    
    /**
     * 根据endpoint类型处理任务
     */
    private String processTaskByEndpoint(String endpoint, JobTaskPayload payload, ChannelDB channel) {
        log.info("处理{}类型的任务，渠道={}", endpoint, channel.getChannelCode());
        
        // 创建EndpointProcessData
        EndpointProcessData processData = createProcessData(payload, channel);
        
        switch (endpoint) {
            case "/v1/chat/completions":
                return processChatCompletionTask(payload, processData);
            case "/v1/audio/transcriptions":
                return processAudioTranscriptionTask(payload, processData);  
            case "/v1/embeddings":
                return processEmbeddingTask(payload, processData);
            default:
                throw new IllegalArgumentException("不支持的endpoint类型: " + endpoint);
        }
    }
    
    /**
     * 处理聊天补全任务
     */
    private String processChatCompletionTask(JobTaskPayload payload, EndpointProcessData processData) {
        try {
            // 调用现有的协议适配器处理
            Object result = adaptorManager.completion(processData);
            return result != null ? result.toString() : "success";
        } catch (Exception e) {
            log.error("处理chat completion任务失败", e);
            throw e;
        }
    }
    
    /**
     * 处理音频转录任务
     */
    private String processAudioTranscriptionTask(JobTaskPayload payload, EndpointProcessData processData) {
        try {
            Object result = adaptorManager.transcription(processData);
            return result != null ? result.toString() : "success";
        } catch (Exception e) {
            log.error("处理audio transcription任务失败", e);
            throw e;
        }
    }
    
    /**
     * 处理嵌入向量任务
     */
    private String processEmbeddingTask(JobTaskPayload payload, EndpointProcessData processData) {
        try {
            Object result = adaptorManager.embedding(processData);
            return result != null ? result.toString() : "success";
        } catch (Exception e) {
            log.error("处理embedding任务失败", e);
            throw e;
        }
    }
    
    /**
     * 创建EndpointProcessData
     */
    private EndpointProcessData createProcessData(JobTaskPayload payload, ChannelDB channel) {
        EndpointProcessData processData = new EndpointProcessData();
        
        // 设置基本信息
        processData.setAkCode(payload.getAkCode());
        processData.setEntityType(channel.getEntityType());
        processData.setEntityCode(channel.getEntityCode());
        processData.setChannelCode(channel.getChannelCode());
        
        // 设置请求数据
        processData.setRequestBody(payload.getRequestBody());
        processData.setHeaders(payload.getHeaders());
        
        return processData;
    }
    
    /**
     * 检查渠道是否空闲
     * TODO: 集成之前实现的ChannelIdleDetector逻辑
     */
    private boolean isChannelIdle(String channelCode) {
        try {
            // 检查渠道是否不可用
            java.util.Set<String> unavailableChannels = metricsManager.getAllUnavailableChannels(
                java.util.Arrays.asList(channelCode)
            );
            if (unavailableChannels.contains(channelCode)) {
                return false;
            }
            
            // 这里可以添加更详细的空闲检测逻辑
            // 比如基于RPM阈值的检测
            
            return true;
        } catch (Exception e) {
            log.warn("检查渠道{}空闲状态时发生异常: {}", channelCode, e.getMessage());
            return false;
        }
    }
    
    /**
     * JobQueue任务载荷
     */
    public static class JobTaskPayload {
        private String akCode;
        private String channelCode;
        private String endpoint;
        private String requestBody;
        private java.util.Map<String, String> headers;
        
        // Getters and Setters
        public String getAkCode() {
            return akCode;
        }
        
        public void setAkCode(String akCode) {
            this.akCode = akCode;
        }
        
        public String getChannelCode() {
            return channelCode;
        }
        
        public void setChannelCode(String channelCode) {
            this.channelCode = channelCode;
        }
        
        public String getEndpoint() {
            return endpoint;
        }
        
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
        
        public String getRequestBody() {
            return requestBody;
        }
        
        public void setRequestBody(String requestBody) {
            this.requestBody = requestBody;
        }
        
        public java.util.Map<String, String> getHeaders() {
            return headers;
        }
        
        public void setHeaders(java.util.Map<String, String> headers) {
            this.headers = headers;
        }
    }
}