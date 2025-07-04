package com.ke.bella.openapi.service;

import com.ke.bella.job.queue.config.JobQueueProperties;
import com.ke.bella.job.queue.worker.JobQueueWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

/**
 * JobQueueWorker管理器
 * 集成ChannelIdleDetector，实现基于channel空闲状态的智能任务拉取
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "bella.job-queue.worker.enabled", havingValue = "true")
@DependsOn("channelIdleDetector")
public class JobQueueWorkerManager {

    @Autowired
    private JobQueueProperties jobQueueProperties;

    @Autowired
    private ChannelIdleDetector channelIdleDetector;

    @Autowired
    private ChannelAwareTaskHandler channelAwareTaskHandler;

    private final List<JobQueueWorker> workers = new ArrayList<>();

    @PostConstruct
    public void init() {
        log.info("Initializing JobQueueWorkerManager with idle detection...");
        
        // 创建支持的endpoint workers
        createWorkerForEndpoint("/v1/chat/completions", "chat-completion");
        createWorkerForEndpoint("/v1/audio/transcriptions", "audio-transcription");  
        createWorkerForEndpoint("/v1/embeddings", "embeddings");
        
        log.info("JobQueueWorkerManager initialized with {} workers", workers.size());
    }

    private void createWorkerForEndpoint(String endpoint, String queueName) {
        try {
            // 获取需要监控的channel列表
            List<String> monitoredChannels = getMonitoredChannelsForEndpoint(endpoint);
            
            // 创建支持空闲检测的JobQueueWorker
            IdleAwareJobQueueWorker worker = new IdleAwareJobQueueWorker(
                jobQueueProperties.getUrl(), 
                endpoint, 
                queueName,
                channelIdleDetector,
                monitoredChannels
            );
            
            // 使用ChannelAwareTaskHandler处理任务
            worker.setTaskHandler(channelAwareTaskHandler);
            
            // 配置worker参数
            worker.setPollSize(getWorkerPollSize());
            worker.setRetryQueueSize(getWorkerRetryQueueSize());
            
            // 启动worker
            worker.start();
            workers.add(worker);
            
            log.info("Created and started IdleAwareJobQueueWorker for endpoint: {}, queue: {}, monitoring {} channels", 
                    endpoint, queueName, monitoredChannels.size());
            
        } catch (Exception e) {
            log.error("Failed to create JobQueueWorker for endpoint {}: {}", endpoint, e.getMessage(), e);
        }
    }
    
    /**
     * 获取指定endpoint需要监控的channel列表
     */
    private List<String> getMonitoredChannelsForEndpoint(String endpoint) {
        // TODO: 根据endpoint获取相关的channel列表
        // 这里应该从ChannelService或数据库中获取支持该endpoint的channel列表
        // 暂时返回一个示例实现
        log.warn("getMonitoredChannelsForEndpoint() is not fully implemented - using placeholder for endpoint: {}", endpoint);
        return List.of("channel-1", "channel-2", "channel-3");
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down JobQueueWorkerManager...");
        
        workers.forEach(worker -> {
            try {
                worker.stop();
            } catch (Exception e) {
                log.error("Error stopping JobQueueWorker: {}", e.getMessage(), e);
            }
        });
        
        workers.clear();
        log.info("JobQueueWorkerManager shutdown complete");
    }

    /**
     * 获取worker轮询大小配置
     */
    private int getWorkerPollSize() {
        return jobQueueProperties.getWorker().getPollSize();
    }

    /**
     * 获取worker重试队列大小配置
     */
    private int getWorkerRetryQueueSize() {
        return jobQueueProperties.getWorker().getRetryQueueSize();
    }

    /**
     * 获取活跃的worker数量
     */
    public int getActiveWorkerCount() {
        return workers.size();
    }

    /**
     * 获取空闲检测器状态信息
     */
    public String getIdleDetectorStatus() {
        try {
            double thresholdRatio = channelIdleDetector.getIdleThresholdRatio();
            return String.format("Idle threshold ratio: %.2f", thresholdRatio);
        } catch (Exception e) {
            return "Error getting idle detector status: " + e.getMessage();
        }
    }
}