package com.ke.bella.openapi.protocol.limiter;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ConcurrentPermitLimiter {

    private static final String SCRIPT_NAME = "/concurrent_permit";
    private static final String OPERATION_ACQUIRE = "ACQUIRE";
    private static final String OPERATION_RELEASE = "RELEASE";

    @Autowired
    private LuaScriptExecutor executor;

    public String tryAcquire(String key, int maxPermits, int ttlSeconds) {
        try {
            return acquire(key, maxPermits, ttlSeconds);
        } catch (Exception e) {
            log.warn("Failed to acquire Redis concurrent permit: key={}, maxPermits={}", key, maxPermits, e);
            return null;
        }
    }

    public String acquire(String key, int maxPermits, int ttlSeconds) throws IOException {
        String permitId = UUID.randomUUID().toString();
        return acquire(key, maxPermits, ttlSeconds, permitId) ? permitId : null;
    }

    public boolean acquire(String key, int maxPermits, int ttlSeconds, String permitId) throws IOException {
        if(StringUtils.isBlank(key) || StringUtils.isBlank(permitId) || maxPermits <= 0 || ttlSeconds <= 0) {
            return false;
        }

        List<Object> keys = Lists.newArrayList(key);
        List<Object> params = new ArrayList<>();
        params.add(OPERATION_ACQUIRE);
        params.add(maxPermits);
        params.add(ttlSeconds);
        params.add(permitId);
        params.add(System.currentTimeMillis());

        Object result = executor.execute(SCRIPT_NAME, ScriptType.limiter, keys, params);
        return result instanceof Number && ((Number) result).intValue() == 1;
    }

    public boolean tryRelease(String key, String permitId) {
        try {
            return release(key, permitId);
        } catch (Exception e) {
            log.warn("Failed to release Redis concurrent permit: key={}", key, e);
            return false;
        }
    }

    public boolean release(String key, String permitId) throws IOException {
        if(StringUtils.isBlank(key) || StringUtils.isBlank(permitId)) {
            return false;
        }

        List<Object> keys = Lists.newArrayList(key);
        List<Object> params = new ArrayList<>();
        params.add(OPERATION_RELEASE);
        params.add(permitId);

        Object result = executor.execute(SCRIPT_NAME, ScriptType.limiter, keys, params);
        return result instanceof Number && ((Number) result).intValue() == 1;
    }
}
