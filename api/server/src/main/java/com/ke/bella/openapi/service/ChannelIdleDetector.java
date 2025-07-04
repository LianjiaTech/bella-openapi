package com.ke.bella.openapi.service;

import com.ke.bella.openapi.db.Condition;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.common.EntityConstants.ACTIVE;

/**
 * Channel空闲状态检测器
 * 基于现有MetricsManager系统判断channel是否处于空闲状态
 */
@Slf4j
@Component
public class ChannelIdleDetector {

    @Autowired
    private MetricsManager metricsManager;

    @Autowired
    private ChannelService channelService;

    /**
     * 获取所有空闲的channel代码列表
     * 
     * @return 空闲的channel代码列表
     */
    public List<String> getIdleChannels() {
        try {
            // 1. 获取所有活跃的channels
            List<ChannelDB> activeChannels = channelService.listByCondition(
                Condition.ChannelCondition.builder()
                    .status(ACTIVE)
                    .build()
            );
            
            List<String> activeChannelCodes = activeChannels.stream()
                    .map(ChannelDB::getChannelCode)
                    .collect(Collectors.toList());
            
            // 2. 过滤掉不可用的channels (基于现有metrics系统)
            List<String> unavailableChannels = metricsManager.getAllUnavailableChannels();
            
            // 3. 可用但相对空闲的channels就是我们要找的
            List<String> availableChannels = activeChannelCodes.stream()
                    .filter(channelCode -> !unavailableChannels.contains(channelCode))
                    .collect(Collectors.toList());
            
            log.debug("Active channels: {}, Unavailable channels: {}, Available/Idle channels: {}", 
                     activeChannelCodes.size(), unavailableChannels.size(), availableChannels.size());
            
            return availableChannels;
            
        } catch (Exception e) {
            log.error("Failed to detect idle channels", e);
            return List.of();
        }
    }

    /**
     * 检查指定channel是否空闲
     * 
     * @param channelCode channel代码
     * @return true if idle, false if busy or unavailable
     */
    public boolean isChannelIdle(String channelCode) {
        try {
            // 基于现有metrics系统判断：如果channel没有被标记为不可用，则认为是空闲的
            List<String> unavailableChannels = metricsManager.getAllUnavailableChannels();
            boolean isAvailable = !unavailableChannels.contains(channelCode);
            
            if (isAvailable) {
                log.debug("Channel {} is idle/available", channelCode);
            }
            
            return isAvailable;
            
        } catch (Exception e) {
            log.error("Failed to check if channel {} is idle", channelCode, e);
            return false;
        }
    }

    /**
     * 根据endpoint过滤空闲channels
     * 
     * @param endpoint API endpoint (如 /v1/chat/completions)
     * @return 支持该endpoint的空闲channel列表
     */
    public List<String> getIdleChannelsForEndpoint(String endpoint) {
        List<String> idleChannels = getIdleChannels();
        
        // TODO: 根据endpoint过滤支持的channels
        // 当前简化实现：返回所有空闲channels
        // 后续可以根据channel配置的支持的endpoints进行过滤
        
        return idleChannels;
    }
}