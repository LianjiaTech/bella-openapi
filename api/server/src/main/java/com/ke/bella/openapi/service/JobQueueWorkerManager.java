package com.ke.bella.openapi.service;

import com.ke.bella.job.queue.config.JobQueueProperties;
import com.ke.bella.job.queue.worker.JobQueueWorker;
import com.ke.bella.job.queue.worker.TaskHandler;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JobQueue Worker管理器 - 基于渠道启动Worker
 * 
 * 每个配置了jobQueue的渠道启动一个独立的Worker实例
 * 使用channelCode作为queueName
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "bella.job-queue.worker.enabled", havingValue = "true")
public class JobQueueWorkerManager {

    @Autowired
    private ChannelService channelService;
    
    @Autowired
    private JobQueueProperties jobQueueProperties;
    
    @Autowired
    private TaskHandler taskHandler;
    
    private final Map<String, JobQueueWorker> workers = new ConcurrentHashMap<>();
    
    /**
     * 应用启动后初始化所有渠道Worker
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeWorkers() {
        log.info("开始初始化JobQueue Workers...");
        
        try {
            // 获取所有激活的渠道
            List<String> entityTypes = List.of("endpoint", "model");
            
            for (String entityType : entityTypes) {
                // 获取该实体类型下的所有渠道（通过扫描数据库）
                List<ChannelDB> channels = scanChannelsByEntityType(entityType);
                
                for (ChannelDB channel : channels) {
                    initializeWorkerForChannel(channel);
                }
            }
            
            log.info("JobQueue Workers初始化完成，共启动{}个worker", workers.size());
            
        } catch (Exception e) {
            log.error("初始化JobQueue Workers失败", e);
        }
    }
    
    /**
     * 为单个渠道初始化Worker
     */
    private void initializeWorkerForChannel(ChannelDB channel) {
        try {
            // 解析渠道配置
            ChannelJobQueueConfig config = parseChannelJobQueueConfig(channel);
            if (config == null || !config.isJobQueueWorkerEnabled()) {
                return;
            }
            
            String channelCode = channel.getChannelCode();
            String entityCode = channel.getEntityCode();
            
            log.info("为渠道{}启动JobQueue Worker, entityCode={}", channelCode, entityCode);
            
            // 创建Worker实例
            JobQueueWorker worker = new JobQueueWorker(
                jobQueueProperties.getUrl(),
                entityCode, // endpoint作为worker的endpoint参数
                channelCode // channelCode作为queueName
            );
            
            // 设置参数
            worker.setPollSize(config.getPollSize());
            worker.setRetryQueueSize(config.getRetryQueueSize());
            worker.setTaskHandler(taskHandler);
            
            // 启动Worker
            worker.start();
            workers.put(channelCode, worker);
            
            log.info("渠道{}的JobQueue Worker启动成功", channelCode);
            
        } catch (Exception e) {
            log.error("为渠道{}初始化JobQueue Worker失败", channel.getChannelCode(), e);
        }
    }
    
    /**
     * 扫描指定实体类型的所有渠道
     */
    private List<ChannelDB> scanChannelsByEntityType(String entityType) {
        // 由于需要扫描所有渠道，这里使用条件查询
        return channelService.listByCondition(
            com.ke.bella.openapi.metadata.Condition.ChannelCondition.builder()
                .status("active")
                .entityType(entityType)
                .build()
        );
    }
    
    /**
     * 解析渠道的JobQueue配置
     */
    private ChannelJobQueueConfig parseChannelJobQueueConfig(ChannelDB channel) {
        if (!StringUtils.hasText(channel.getChannelInfo())) {
            return null;
        }
        
        try {
            Map<String, Object> channelInfo = JacksonUtils.deserialize(channel.getChannelInfo(), Map.class);
            Object jobQueueConfig = channelInfo.get("jobQueue");
            
            if (jobQueueConfig == null) {
                return null;
            }
            
            return JacksonUtils.deserialize(
                JacksonUtils.serialize(jobQueueConfig), 
                ChannelJobQueueConfig.class
            );
            
        } catch (Exception e) {
            log.warn("解析渠道{}的jobQueue配置失败: {}", channel.getChannelCode(), e.getMessage());
            return null;
        }
    }
    
    /**
     * 停止指定渠道的Worker
     */
    public void stopWorker(String channelCode) {
        JobQueueWorker worker = workers.remove(channelCode);
        if (worker != null) {
            worker.stop();
            log.info("渠道{}的JobQueue Worker已停止", channelCode);
        }
    }
    
    /**
     * 应用关闭时清理所有Worker
     */
    @PreDestroy
    public void shutdown() {
        log.info("开始关闭所有JobQueue Workers...");
        
        workers.forEach((channelCode, worker) -> {
            try {
                worker.stop();
                log.info("渠道{}的Worker已关闭", channelCode);
            } catch (Exception e) {
                log.error("关闭渠道{}的Worker失败", channelCode, e);
            }
        });
        
        workers.clear();
        log.info("所有JobQueue Workers已关闭");
    }
    
    /**
     * 获取Worker状态信息
     */
    public Map<String, String> getWorkerStatus() {
        Map<String, String> status = new ConcurrentHashMap<>();
        workers.forEach((channelCode, worker) -> {
            status.put(channelCode, "RUNNING");
        });
        return status;
    }
    
    /**
     * 渠道JobQueue配置类
     */
    public static class ChannelJobQueueConfig {
        private boolean jobQueueWorkerEnabled = false;
        private int pollSize = 10;
        private int retryQueueSize = 1000;
        
        public boolean isJobQueueWorkerEnabled() {
            return jobQueueWorkerEnabled;
        }
        
        public void setJobQueueWorkerEnabled(boolean jobQueueWorkerEnabled) {
            this.jobQueueWorkerEnabled = jobQueueWorkerEnabled;
        }
        
        public int getPollSize() {
            return pollSize;
        }
        
        public void setPollSize(int pollSize) {
            this.pollSize = pollSize;
        }
        
        public int getRetryQueueSize() {
            return retryQueueSize;
        }
        
        public void setRetryQueueSize(int retryQueueSize) {
            this.retryQueueSize = retryQueueSize;
        }
    }
}