package com.ke.bella.openapi.service;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VideoTaskMetadataStore {

    private static final String CHANNEL_VIDEO_ID_KEY_PREFIX = "bella:openapi:video:channel-video-id:";
    private static final long CHANNEL_VIDEO_ID_TTL_HOURS = 25;

    private final RedissonClient redissonClient;

    public VideoTaskMetadataStore(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public void saveChannelVideoId(String taskId, String channelVideoId) {
        if(StringUtils.isBlank(taskId) || StringUtils.isBlank(channelVideoId)) {
            return;
        }

        try {
            RBucket<String> bucket = redissonClient.getBucket(key(taskId), StringCodec.INSTANCE);
            bucket.set(channelVideoId, CHANNEL_VIDEO_ID_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to save channel video id metadata, taskId: {}", taskId, e);
        }
    }

    public String getChannelVideoId(String taskId) {
        if(StringUtils.isBlank(taskId)) {
            return null;
        }

        try {
            RBucket<String> bucket = redissonClient.getBucket(key(taskId), StringCodec.INSTANCE);
            return bucket.get();
        } catch (Exception e) {
            log.warn("Failed to read channel video id metadata, taskId: {}", taskId, e);
            return null;
        }
    }

    public void deleteChannelVideoId(String taskId) {
        if(StringUtils.isBlank(taskId)) {
            return;
        }

        try {
            redissonClient.getBucket(key(taskId), StringCodec.INSTANCE).delete();
        } catch (Exception e) {
            log.warn("Failed to delete channel video id metadata, taskId: {}", taskId, e);
        }
    }

    private String key(String taskId) {
        return CHANNEL_VIDEO_ID_KEY_PREFIX + taskId;
    }
}
