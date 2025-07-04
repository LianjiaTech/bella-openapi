package com.ke.bella.openapi.service;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import com.ke.bella.openapi.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ChannelIdleDetector {
    
    @Autowired
    private RedissonClient redisson;
    
    @Autowired
    private MetricsManager metricsManager;
    
    @Autowired
    private LuaScriptExecutor executor;
    
    @Value("${bella.channel.idle.threshold.ratio:0.7}")
    private double idleThresholdRatio;
    
    private static final String RPM_THRESHOLD_KEY_FORMAT = "bella-openapi-channel-metrics:%s:rpm_threshold";
    private static final String REALTIME_RPM_KEY_FORMAT = "bella-openapi-channel-realtime-rpm:%s";
    
    /**
     * 更新实时RPM（在请求开始时调用）
     */
    public void updateRealtimeRPM(EndpointProcessData processData) {
        if (processData.getChannelCode() == null) {
            return;
        }
        
        try {
            String channelCode = processData.getChannelCode();
            long currentTimestamp = DateTimeUtils.getCurrentSeconds();
            
            // 获取平均响应时间，如果没有则使用默认值60秒
            long avgResponseTime = getAverageResponseTime(channelCode);
            
            List<Object> keys = Collections.singletonList(channelCode);
            List<Object> params = new ArrayList<>();
            params.add(currentTimestamp);
            params.add(avgResponseTime);
            
            executor.execute("/realtime_rpm", ScriptType.metrics, keys, params);
            
        } catch (IOException e) {
            log.warn("Failed to update realtime RPM for channel: {}", processData.getChannelCode(), e);
        }
    }
    
    /**
     * 检查单个channel是否空闲
     */
    public boolean isChannelIdle(String channelCode) {
        try {
            // 1. 检查channel是否可用
            if (metricsManager.getAllUnavailableChannels(Collections.singletonList(channelCode)).contains(channelCode)) {
                return false; // 不可用的channel不是空闲的
            }
            
            // 2. 获取RPM阈值
            String thresholdKey = String.format(RPM_THRESHOLD_KEY_FORMAT, channelCode);
            Object thresholdObj = redisson.getBucket(thresholdKey).get();
            
            if (thresholdObj == null) {
                return true; // 没有阈值说明从未不可用过，可以认为是空闲的
            }
            
            long rpmThreshold = Long.parseLong(thresholdObj.toString());
            
            // 3. 计算当前实时RPM
            long currentRPM = getCurrentRealtimeRPM(channelCode);
            
            // 4. 判断是否空闲（当前RPM < 阈值 * 空闲比例）
            long idleThreshold = (long) (rpmThreshold * idleThresholdRatio);
            
            log.debug("Channel {} idle check: currentRPM={}, threshold={}, idleThreshold={}, idle={}", 
                    channelCode, currentRPM, rpmThreshold, idleThreshold, currentRPM < idleThreshold);
            
            return currentRPM < idleThreshold;
            
        } catch (Exception e) {
            log.warn("Failed to check idle status for channel: {}", channelCode, e);
            return false; // 出错时保守处理，认为不空闲
        }
    }
    
    /**
     * 获取所有空闲的channels
     */
    public List<String> getIdleChannels(Collection<String> channelCodes) {
        return channelCodes.stream()
                .filter(this::isChannelIdle)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取当前实时RPM
     */
    private long getCurrentRealtimeRPM(String channelCode) {
        long currentTimestamp = DateTimeUtils.getCurrentSeconds();
        long windowStart = currentTimestamp - 60; // 60秒窗口
        
        String rpmKeyPrefix = String.format(REALTIME_RPM_KEY_FORMAT, channelCode);
        
        long totalRPM = 0;
        for (long ts = windowStart; ts <= currentTimestamp; ts++) {
            String minuteKey = rpmKeyPrefix + ":" + ts;
            Object count = redisson.getBucket(minuteKey).get();
            if (count != null) {
                totalRPM += Long.parseLong(count.toString());
            }
        }
        
        return totalRPM;
    }
    
    /**
     * 获取平均响应时间（使用ttlt metrics）
     */
    private long getAverageResponseTime(String channelCode) {
        try {
            Map<String, Map<String, Object>> metrics = metricsManager.queryMetrics(
                    "/v1/chat/completions", Collections.singletonList(channelCode));
            
            Map<String, Object> channelMetrics = metrics.get(channelCode);
            if (channelMetrics != null && channelMetrics.containsKey("ttlt")) {
                Object ttltObj = channelMetrics.get("ttlt");
                if (ttltObj != null) {
                    long ttlt = Long.parseLong(ttltObj.toString());
                    Object completedObj = channelMetrics.get("completed");
                    if (completedObj != null) {
                        long completed = Long.parseLong(completedObj.toString());
                        if (completed > 0) {
                            return ttlt / completed; // 平均响应时间（毫秒）
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get average response time for channel: {}", channelCode, e);
        }
        
        return 60000; // 默认60秒
    }
}