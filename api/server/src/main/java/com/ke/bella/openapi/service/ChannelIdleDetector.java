package com.ke.bella.openapi.service;

import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.script.LuaScriptManager;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Channel空闲状态检测器
 * 基于TPM阈值检测channel是否空闲，支持从job-queue拉取任务
 */
@Slf4j
@Service
public class ChannelIdleDetector {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private MetricsManager metricsManager;

    @Autowired
    private LuaScriptManager luaScriptManager;

    @Value("${bella.openapi.channel.idle.threshold.ratio:0.7}")
    private double idleThresholdRatio;

    @Value("${bella.openapi.channel.idle.avg-tokens-per-request:100}")
    private int avgTokensPerRequest;

    /**
     * 检测指定channel是否空闲
     * 基于TPM阈值的70%(可配置)判断
     */
    public boolean isChannelIdle(String channelId) {
        try {
            // 检查channel是否可用
            Set<String> unavailableChannels = metricsManager.getAllUnavailableChannels();
            if (unavailableChannels.contains(channelId)) {
                log.debug("Channel {} is unavailable, not idle", channelId);
                return false;
            }

            // 获取TPM阈值
            String tpmThresholdKey = "bella-openapi-channel-metrics:" + channelId + ":tpm_threshold";
            String tpmThresholdStr = redissonClient.getBucket(tpmThresholdKey).get();
            if (tpmThresholdStr == null) {
                log.debug("No TPM threshold found for channel {}, considering not idle", channelId);
                return false;
            }

            double tpmThreshold = Double.parseDouble(tpmThresholdStr.toString());
            double idleThreshold = tpmThreshold * idleThresholdRatio;

            // 获取当前实时RPM
            double currentRpm = getCurrentRealtimeRPM(channelId);
            
            // 估算当前TPM (RPM × 平均每请求token数)
            double estimatedCurrentTpm = currentRpm * avgTokensPerRequest;

            boolean isIdle = estimatedCurrentTpm < idleThreshold;
            
            log.debug("Channel {} - TPM threshold: {}, idle threshold: {}, current estimated TPM: {}, is idle: {}", 
                     channelId, tpmThreshold, idleThreshold, estimatedCurrentTpm, isIdle);

            return isIdle;

        } catch (Exception e) {
            log.error("Error checking idle status for channel {}: {}", channelId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取所有空闲的channel列表
     */
    public List<String> getIdleChannels(List<String> channelIds) {
        List<String> idleChannels = new ArrayList<>();
        
        for (String channelId : channelIds) {
            if (isChannelIdle(channelId)) {
                idleChannels.add(channelId);
            }
        }
        
        log.debug("Found {} idle channels out of {} total channels", idleChannels.size(), channelIds.size());
        return idleChannels;
    }

    /**
     * 更新实时RPM
     * 由MetricsManager调用，每次请求结束时更新
     */
    public void updateRealtimeRPM(String channelId, long avgResponseTimeMs) {
        try {
            String scriptName = "sliding_window_rpm";
            long currentTimestamp = System.currentTimeMillis() / 1000;
            
            // 调用Lua脚本更新实时RPM
            Object result = luaScriptManager.execute(scriptName, 
                List.of(), 
                List.of(channelId, String.valueOf(avgResponseTimeMs), String.valueOf(currentTimestamp)));
            
            log.debug("Updated realtime RPM for channel {}: response time {}ms, result: {}", 
                     channelId, avgResponseTimeMs, result);
                     
        } catch (Exception e) {
            log.error("Error updating realtime RPM for channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    /**
     * 获取当前实时RPM
     */
    private double getCurrentRealtimeRPM(String channelId) {
        try {
            String rpmKey = "bella-openapi-realtime-rpm:" + channelId;
            long currentTimestamp = System.currentTimeMillis() / 1000;
            long currentMinute = (currentTimestamp / 60) * 60;
            
            Double rpm = redissonClient.getScoredSortedSet(rpmKey).getScore(currentMinute);
            return rpm != null ? rpm : 0.0;
            
        } catch (Exception e) {
            log.error("Error getting current RPM for channel {}: {}", channelId, e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * 获取channel的TPM阈值
     */
    public Double getChannelTpmThreshold(String channelId) {
        try {
            String tpmThresholdKey = "bella-openapi-channel-metrics:" + channelId + ":tpm_threshold";
            String tpmThresholdStr = redissonClient.getBucket(tpmThresholdKey).get();
            return tpmThresholdStr != null ? Double.parseDouble(tpmThresholdStr.toString()) : null;
        } catch (Exception e) {
            log.error("Error getting TPM threshold for channel {}: {}", channelId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 设置空闲阈值比例
     */
    public void setIdleThresholdRatio(double ratio) {
        this.idleThresholdRatio = ratio;
    }

    /**
     * 获取空闲阈值比例
     */
    public double getIdleThresholdRatio() {
        return idleThresholdRatio;
    }
}