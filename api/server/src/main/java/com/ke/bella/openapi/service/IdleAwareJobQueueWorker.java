package com.ke.bella.openapi.service;

import com.ke.bella.job.queue.worker.JobQueueWorker;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 支持空闲检测的JobQueueWorker
 * 只在有空闲channel时才拉取新任务
 */
@Slf4j
public class IdleAwareJobQueueWorker extends JobQueueWorker {

    private final ChannelIdleDetector channelIdleDetector;
    private final List<String> monitoredChannels;

    public IdleAwareJobQueueWorker(String url, String endpoint, String queueName, 
                                   ChannelIdleDetector channelIdleDetector, 
                                   List<String> monitoredChannels) {
        super(url, endpoint, queueName);
        this.channelIdleDetector = channelIdleDetector;
        this.monitoredChannels = monitoredChannels;
    }

    @Override
    protected boolean shouldPollNewTasks() {
        try {
            if (channelIdleDetector == null || monitoredChannels == null || monitoredChannels.isEmpty()) {
                log.debug("No idle detector or channels configured, defaulting to poll tasks");
                return true;
            }

            // 检查是否有空闲的channel
            List<String> idleChannels = channelIdleDetector.getIdleChannels(monitoredChannels);
            boolean hasIdleChannels = !idleChannels.isEmpty();
            
            log.debug("IdleAware JobQueueWorker: {} idle channels out of {} total channels", 
                     idleChannels.size(), monitoredChannels.size());
            
            return hasIdleChannels;
            
        } catch (Exception e) {
            log.error("Error checking idle channels: {}", e.getMessage(), e);
            // 发生错误时默认允许拉取任务
            return true;
        }
    }
}