package com.ke.bella.openapi.protocol.limiter;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * QPS 限流管理器
 * 基于滑动窗口计数器实现精确的 QPS 限流
 */
@Component
@Slf4j
public class QpsLimiterManager {
    @Autowired
    private LuaScriptExecutor executor;

    @Autowired
    private RedissonClient redisson;

    private static final String QPS_KEY_FORMAT = "bella-openapi-limiter-qps:%s";

    /**
     * 检查 QPS 限制
     *
     * @param akCode API Key 编码
     * @param qpsLimit QPS 限制值
     * @return true=允许通过, false=拒绝（超限）
     */
    public boolean checkLimit(String akCode, int qpsLimit) {
        if (qpsLimit <= 0) {
            // 未配置限制，放行
            return true;
        }

        String key = String.format(QPS_KEY_FORMAT, akCode);
        long currentTimeMs = System.currentTimeMillis();

        List<Object> keys = Lists.newArrayList(key);
        List<Object> params = new ArrayList<>();
        params.add(qpsLimit);
        params.add(currentTimeMs);

        try {
            // 执行 Lua 脚本
            // 返回格式: [is_allowed, current_count, status]
            @SuppressWarnings("unchecked")
            List<Object> result = (List<Object>) executor.execute("/qps", ScriptType.limiter, keys, params);

            if (result != null && result.size() >= 2) {
                Long isAllowed = (Long) result.get(0);
                Long currentCount = (Long) result.get(1);

                if (isAllowed == 0) {
                    // 被拒绝，记录日志
                    log.warn("QPS limit exceeded for akCode: {}, limit: {}, current: {}",
                             akCode, qpsLimit, currentCount);
                    return false;
                }

                // 通过检查
                if (log.isDebugEnabled()) {
                    log.debug("QPS check passed for akCode: {}, limit: {}, current: {}",
                              akCode, qpsLimit, currentCount);
                }
                return true;
            }

            // 异常情况，默认放行（降级策略）
            log.error("Unexpected result from QPS limiter script: {}", result);
            return true;

        } catch (IOException e) {
            // Redis 异常，记录日志并放行（降级策略）
            log.error("Failed to execute QPS limiter script for akCode: {}, error: {}",
                     akCode, e.getMessage(), e);
            return true;
        } catch (Exception e) {
            log.error("Unexpected error in QPS limiter for akCode: {}, error: {}",
                     akCode, e.getMessage(), e);
            return true;
        }
    }

    /**
     * 获取当前 QPS（用于监控和统计）
     *
     * @param akCode API Key 编码
     * @return 当前 QPS 值
     */
    public Long getCurrentQps(String akCode) {
        String key = String.format(QPS_KEY_FORMAT, akCode);

        try {
            // 读取 HASH 中所有时间片的计数
            Object hashObj = redisson.getBucket(key).get();
            if (hashObj == null) {
                return 0L;
            }

            // 聚合所有时间片的计数
            // 注意：这里简化实现，实际应该只统计最近 1 秒的时间片
            // 更精确的实现需要通过 Lua 脚本或单独的统计逻辑
            return 0L;  // TODO: 后续优化

        } catch (Exception e) {
            log.error("Failed to get current QPS for akCode: {}, error: {}",
                     akCode, e.getMessage(), e);
            return 0L;
        }
    }
}
