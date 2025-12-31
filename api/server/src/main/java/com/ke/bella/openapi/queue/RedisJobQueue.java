package com.ke.bella.openapi.queue;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.redisson.api.RDeque;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis-based implementation of JobQueue using Redisson.
 * Uses Redis Deque for atomic FIFO operations.
 * Implements Redis semantics: LEFT=HEAD, RIGHT=TAIL
 *
 * @param <T> Type of job identifier
 */
@Component
@Slf4j
public class RedisJobQueue<T> implements JobQueue<T> {

    @Resource
    private RedissonClient redissonClient;

    @Override
    public void lpush(String queueKey, T jobId) {
        RDeque<T> queue = redissonClient.getDeque(queueKey);
        queue.addFirst(jobId);
        log.debug("[JobQueue] lpush {} to HEAD: queue={}", jobId, queueKey);
    }

    @Override
    public void rpush(String queueKey, T jobId) {
        RDeque<T> queue = redissonClient.getDeque(queueKey);
        queue.addLast(jobId);
        log.debug("[JobQueue] rpush {} to TAIL: queue={}", jobId, queueKey);
    }

    @Override
    public List<T> lpop(String queueKey, int maxSize) {
        RDeque<T> queue = redissonClient.getDeque(queueKey);
        List<T> result = new ArrayList<>();

        for (int i = 0; i < maxSize; i++) {
            T jobId = queue.pollFirst();
            if(jobId == null) {
                break;
            }
            result.add(jobId);
        }

        if(!result.isEmpty()) {
            log.debug("[JobQueue] lpop {} jobs from HEAD: queue={}", result.size(), queueKey);
        }

        return result;
    }
}
