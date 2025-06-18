package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import com.ke.bella.openapi.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Channel空闲状态检测器
 * 基于TPM阈值和RPM实时计算判断channel是否空闲
 */
@Component
@Slf4j
public class ChannelIdleDetector {
    
    @Autowired
    private RedissonClient redisson;
    
    @Autowired
    private LuaScriptExecutor luaScriptExecutor;
    
    @Value("${bella.openapi.channel.idle.threshold.ratio:0.7}")
    private double idleThresholdRatio;
    
    private static final String TPM_THRESHOLD_KEY = "bella-openapi-channel-metrics:%s:tpm_threshold";
    private static final String CHANNEL_METRICS_TOTAL_KEY = "bella-openapi-channel-metrics:%s:total";
    private static final String REALTIME_RPM_KEY = "bella-openapi-channel-realtime-rpm:%s:current";
    
    /**
     * 获取空闲的channel列表
     * @param channelCodes 要检查的channel代码列表
     * @return 空闲的channel代码列表
     */
    public List<String> getIdleChannels(Collection<String> channelCodes) {
        List<String> idleChannels = new ArrayList<>();
        
        for (String channelCode : channelCodes) {
            if (isChannelIdle(channelCode)) {
                idleChannels.add(channelCode);
            }
        }
        
        log.debug("Detected idle channels: {}", idleChannels);
        return idleChannels;
    }
    
    /**
     * 判断单个channel是否空闲
     * @param channelCode channel代码
     * @return true表示空闲，false表示忙碌
     */
    public boolean isChannelIdle(String channelCode) {
        try {
            // 获取TPM阈值
            String thresholdKey = String.format(TPM_THRESHOLD_KEY, channelCode);
            String thresholdStr = redisson.getBucket(thresholdKey).get();
            
            if (thresholdStr == null) {
                // 没有TPM阈值记录，认为channel可用
                log.debug("No TPM threshold found for channel: {}, considering it idle", channelCode);
                return true;
            }
            
            double tpmThreshold = Double.parseDouble(thresholdStr);
            double idleThreshold = tpmThreshold * idleThresholdRatio;
            
            // 获取当前TPM（基于实时RPM估算）
            double currentTpm = getCurrentEstimatedTPM(channelCode);
            
            boolean isIdle = currentTpm < idleThreshold;
            log.debug("Channel {} - TPM threshold: {}, Current estimated TPM: {}, Idle threshold: {}, Is idle: {}", 
                channelCode, tpmThreshold, currentTpm, idleThreshold, isIdle);
            
            return isIdle;
            
        } catch (Exception e) {
            log.error("Error checking idle status for channel: {}", channelCode, e);
            // 出错时认为channel不空闲，避免错误拉取任务
            return false;
        }
    }
    
    /**
     * 基于实时RPM估算当前TPM
     * 由于TPM统计不是实时的，使用RPM作为粗略估算
     */
    private double getCurrentEstimatedTPM(String channelCode) {
        // 获取实时RPM
        String rpmKey = String.format(REALTIME_RPM_KEY, channelCode);
        String rpmStr = redisson.getBucket(rpmKey).get();
        
        if (rpmStr == null) {
            return 0.0;
        }
        
        double currentRpm = Double.parseDouble(rpmStr);
        
        // 获取历史平均每请求token数来估算TPM
        double avgTokensPerRequest = getAverageTokensPerRequest(channelCode);
        
        // 估算TPM = RPM * 平均每请求token数
        return currentRpm * avgTokensPerRequest;
    }
    
    /**
     * 获取channel的平均每请求token数
     */
    private double getAverageTokensPerRequest(String channelCode) {
        try {
            String totalKey = String.format(CHANNEL_METRICS_TOTAL_KEY, channelCode);
            Map<String, String> metrics = redisson.getMap(totalKey).readAllMap();
            
            String inputTokensStr = metrics.get("input_tokens");
            String outputTokensStr = metrics.get("output_tokens");
            String completedStr = metrics.get("completed");
            
            if (inputTokensStr == null || outputTokensStr == null || completedStr == null) {
                // 没有历史数据时使用默认值
                return 100.0; // 假设平均每请求100 tokens
            }
            
            double totalTokens = Double.parseDouble(inputTokensStr) + Double.parseDouble(outputTokensStr);
            double totalRequests = Double.parseDouble(completedStr);
            
            if (totalRequests == 0) {
                return 100.0;
            }
            
            double avgTokens = totalTokens / totalRequests;
            return Math.max(avgTokens, 1.0); // 至少1个token
            
        } catch (Exception e) {
            log.warn("Error calculating average tokens per request for channel {}, using default", channelCode, e);
            return 100.0;
        }
    }
    
    /**
     * 更新channel的实时RPM
     */
    public void updateRealtimeRPM(String channelCode, double avgResponseTimeMs) {
        try {
            List<Object> keys = List.of(channelCode);
            List<Object> args = List.of(
                DateTimeUtils.getCurrentSeconds(),
                (int) avgResponseTimeMs
            );
            
            // 执行滑动窗口RPM计算lua脚本
            Object result = luaScriptExecutor.execute("/v1/chat/completions", 
                ScriptType.custom, keys, args, "sliding_window_rpm");
            
            log.debug("Updated realtime RPM for channel {}, result: {}", channelCode, result);
            
        } catch (Exception e) {
            log.error("Error updating realtime RPM for channel: {}", channelCode, e);
        }
    }
}