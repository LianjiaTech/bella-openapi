package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.protocol.metrics.ChannelIdleDetector;
import com.ke.bella.openapi.service.JobQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Job Queue工作器
 * 定期检测空闲channel并从job-queue中拉取任务执行
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "bella.openapi.job-queue.enabled", havingValue = "true")
public class JobQueueWorker {
    
    @Autowired
    private ChannelIdleDetector channelIdleDetector;
    
    @Autowired
    private JobQueueService jobQueueService;
    
    @Autowired
    private JobTaskProcessor taskProcessor;
    
    @Value("${bella.openapi.job-queue.worker-interval:5000}")
    private long workerInterval;
    
    @Value("${bella.openapi.job-queue.batch-size:10}")
    private int batchSize;
    
    @Value("${bella.openapi.job-queue.max-concurrent-per-channel:5}")
    private int maxConcurrentPerChannel;
    
    /**
     * 定期检查空闲channel并拉取任务
     */
    @Scheduled(fixedDelayString = "${bella.openapi.job-queue.worker-interval:5000}")
    public void processIdleTasks() {
        try {
            log.debug("Starting job queue task processing cycle");
            
            // 1. 获取所有可用的channel codes
            Collection<String> availableChannelCodes = getAvailableChannelCodes();
            if (availableChannelCodes.isEmpty()) {
                log.debug("No available channels found");
                return;
            }
            
            // 2. 检测空闲的channels
            List<String> idleChannels = channelIdleDetector.getIdleChannels(availableChannelCodes);
            if (idleChannels.isEmpty()) {
                log.debug("No idle channels found");
                return;
            }
            
            log.info("Found {} idle channels: {}", idleChannels.size(), idleChannels);
            
            // 3. 为每个空闲channel拉取并处理任务
            for (String channelCode : idleChannels) {
                processTasksForChannel(channelCode);
            }
            
        } catch (Exception e) {
            log.error("Error during job queue task processing", e);
        }
    }
    
    /**
     * 为指定channel处理任务
     */
    private void processTasksForChannel(String channelCode) {
        try {
            // 检查channel当前正在处理的任务数
            int currentConcurrency = taskProcessor.getCurrentConcurrency(channelCode);
            if (currentConcurrency >= maxConcurrentPerChannel) {
                log.debug("Channel {} has reached max concurrency limit: {}", channelCode, maxConcurrentPerChannel);
                return;
            }
            
            int availableSlots = maxConcurrentPerChannel - currentConcurrency;
            int tasksToFetch = Math.min(batchSize, availableSlots);
            
            // 从job queue拉取任务
            List<JobTask> tasks = pullTasksForChannel(channelCode, tasksToFetch);
            
            if (!tasks.isEmpty()) {
                log.info("Pulled {} tasks for channel {}", tasks.size(), channelCode);
                
                // 提交任务到处理器
                for (JobTask task : tasks) {
                    taskProcessor.submitTask(channelCode, task);
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing tasks for channel: {}", channelCode, e);
        }
    }
    
    /**
     * 从job queue为指定channel拉取任务
     */
    private List<JobTask> pullTasksForChannel(String channelCode, int maxTasks) {
        // 这里需要根据实际的job queue实现来调整
        // 目前假设job queue支持按channel过滤任务
        try {
            return jobQueueService.pullTasksByChannel(channelCode, maxTasks);
        } catch (Exception e) {
            log.error("Error pulling tasks for channel: {}", channelCode, e);
            return List.of();
        }
    }
    
    /**
     * 获取所有可用的channel codes
     * 这里需要根据实际的channel管理实现来调整
     */
    private Collection<String> getAvailableChannelCodes() {
        // 这里应该从数据库或配置中获取所有活跃的channel codes
        // 暂时返回空列表，需要根据实际情况实现
        try {
            return taskProcessor.getAllAvailableChannelCodes();
        } catch (Exception e) {
            log.error("Error getting available channel codes", e);
            return List.of();
        }
    }
}