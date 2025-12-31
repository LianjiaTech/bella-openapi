package com.ke.bella.openapi.protocol.limiter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import com.ke.bella.openapi.utils.DateTimeUtils;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ChannelRpmLimiter {

    @Autowired
    private LuaScriptExecutor executor;

    private static final String CHANNEL_RPM_KEY_FORMAT = "bella-channel-rpm:%s";
    private static final int DEFAULT_WINDOW = 60;
    private static final int DEFAULT_SEGMENT_SIZE = 10;

    public boolean consumeRpm(String channelCode, int rpmLimit) {
        String key = String.format(CHANNEL_RPM_KEY_FORMAT, channelCode);
        long now = DateTimeUtils.getCurrentSeconds();

        List<Object> keys = Lists.newArrayList(key);
        List<Object> params = new ArrayList<>();
        params.add(rpmLimit);
        params.add(DEFAULT_WINDOW);
        params.add(DEFAULT_SEGMENT_SIZE);
        params.add(1);
        params.add(now);

        try {
            List<Object> result = (List<Object>) executor.execute("/channel_rpm", ScriptType.limiter, keys, params);

            Integer pass = ((Number) result.get(0)).intValue();
            if (pass == 1) {
                Integer currentUsage = ((Number) result.get(1)).intValue();
                log.debug("Channel RPM consumed: channelCode={}, currentUsage={}, limit={}",
                    channelCode, currentUsage, rpmLimit);
                return true;
            } else {
                log.warn("Channel RPM limit exceeded: channelCode={}, limit={}", channelCode, rpmLimit);
                return false;
            }
        } catch (IOException e) {
            log.error("Failed to consume channel RPM: channelCode={}", channelCode, e);
            return false;
        }
    }

    public RpmStatus getRpmStatus(String channelCode, int rpmLimit) {
        String key = String.format(CHANNEL_RPM_KEY_FORMAT, channelCode);
        long now = DateTimeUtils.getCurrentSeconds();

        List<Object> keys = Lists.newArrayList(key);
        List<Object> params = new ArrayList<>();
        params.add(rpmLimit);
        params.add(DEFAULT_WINDOW);
        params.add(DEFAULT_SEGMENT_SIZE);
        params.add(0);
        params.add(now);

        try {
            List<Object> result = (List<Object>) executor.execute("/channel_rpm", ScriptType.limiter, keys, params);

            Integer pass = ((Number) result.get(0)).intValue();
            Integer currentUsage = ((Number) result.get(1)).intValue();
            Integer remaining = ((Number) result.get(2)).intValue();
            String message = (String) result.get(3);

            return new RpmStatus(pass == 1, currentUsage, remaining, rpmLimit, message);
        } catch (IOException e) {
            log.error("Failed to get channel RPM status: channelCode={}", channelCode, e);
            return new RpmStatus(false, 0, 0, rpmLimit, "Error: " + e.getMessage());
        }
    }

    public static class RpmStatus {
        private final boolean available;
        private final int currentUsage;
        private final int remaining;
        private final int limit;
        private final String message;

        public RpmStatus(boolean available, int currentUsage, int remaining, int limit, String message) {
            this.available = available;
            this.currentUsage = currentUsage;
            this.remaining = remaining;
            this.limit = limit;
            this.message = message;
        }

        public boolean isAvailable() {
            return available;
        }

        public int getCurrentUsage() {
            return currentUsage;
        }

        public int getRemaining() {
            return remaining;
        }

        public int getLimit() {
            return limit;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("RpmStatus[available=%s, usage=%d/%d, remaining=%d, message=%s]",
                available, currentUsage, limit, remaining, message);
        }
    }
}
