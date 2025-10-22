package com.ke.bella.openapi.protocol.limiter;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import com.ke.bella.openapi.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class LimiterManager {
    @Autowired
    private LuaScriptExecutor executor;

    @Autowired
    private RedissonClient redisson;

    private static final String RPM_KEY_FORMAT = "bella-openapi-limiter-rpm:%s:%s";
    private static final String RPM_COUNT_KEY_FORMAT = "bella-openapi-limiter-rpm-count:%s:%s";
    private static final String CONCURRENT_KEY_FORMAT = "bella-openapi-limiter-concurrent:%s:%s";

    public void record(EndpointProcessData processData) {
        if(processData.getChannelCode() == null) {
            return;
        }
        String entityCode = processData.getModel() != null ? processData.getModel() : processData.getEndpoint();
        String akCode = processData.getAkCode();
        String requestId = processData.getRequestId();
        if (entityCode == null || akCode == null) {
            return;
        }
        long currentTimestamp = DateTimeUtils.getCurrentSeconds();
        if(requestId != null) {
            // RPM记录
            incrementRequestCountPerMinute(akCode, entityCode, requestId, currentTimestamp);
        }

        // 减少并发请求计数
        decrementConcurrentCount(akCode, entityCode);
    }

    public void incrementRequestCountPerMinute(String akCode, String entityCode, String requestId, long currentTimestamp) {
        String rpmKey = String.format(RPM_KEY_FORMAT, entityCode, akCode);
        String countKey = String.format(RPM_COUNT_KEY_FORMAT, entityCode, akCode);

        List<Object> keys = Lists.newArrayList(rpmKey, countKey);
        List<Object> params = new ArrayList<>();
        params.add(currentTimestamp);
        params.add(requestId);

        try {
            executor.execute("/rpm", ScriptType.limiter, keys, params);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    public Long getRequestCountPerMinute(String akCode, String entityCode) {
        String countKey = String.format(RPM_COUNT_KEY_FORMAT, entityCode, akCode);
        Object count = redisson.getBucket(countKey).get();
        return count != null ? Long.parseLong(count.toString()) : 0L;
    }

    public void incrementConcurrentCount(String akCode, String entityCode) {
        String concurrentKey = String.format(CONCURRENT_KEY_FORMAT, entityCode, akCode);
        List<Object> keys = Lists.newArrayList(concurrentKey, entityCode);
        List<Object> params = new ArrayList<>();
        params.add("INCR");
        try {
            executor.execute("/concurrent", ScriptType.limiter, keys, params);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    public void decrementConcurrentCount(String akCode, String entityCode) {
        String concurrentKey = String.format(CONCURRENT_KEY_FORMAT, entityCode, akCode);
        List<Object> keys = Lists.newArrayList(concurrentKey, entityCode);
        List<Object> params = new ArrayList<>();
        params.add("DECR");
        try {
            executor.execute("/concurrent", ScriptType.limiter, keys, params);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Long getCurrentConcurrentCount(String akCode, String entityCode) {
        String concurrentKey = String.format(CONCURRENT_KEY_FORMAT, entityCode, akCode);
        Object count = redisson.getBucket(concurrentKey).get();
        return count != null ? Long.parseLong(count.toString()) : 0L;
    }
}
