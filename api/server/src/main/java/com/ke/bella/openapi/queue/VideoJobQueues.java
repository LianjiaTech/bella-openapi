package com.ke.bella.openapi.queue;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

/**
 * Video job queue facade.
 * Encapsulates queue key naming rules and business semantics for video jobs.
 */
@Component
public class VideoJobQueues {

    @Resource
    private JobQueue<String> jobQueue;

    private static final String SUBMIT_QUEUE_PREFIX = "bella:video:submit:";
    private static final String SYNCING_QUEUE_KEY = "bella:video:syncing";

    /**
     * Enqueue a video job to TAIL of submit queue (FIFO, for new tasks)
     */
    public void enqueueForSubmit(String model, String videoId) {
        jobQueue.rpush(SUBMIT_QUEUE_PREFIX + model, videoId);
    }

    /**
     * Enqueue a video job to HEAD of submit queue (priority, for retry tasks)
     */
    public void enqueueForSubmitFirst(String model, String videoId) {
        jobQueue.lpush(SUBMIT_QUEUE_PREFIX + model, videoId);
    }

    /**
     * Dequeue a batch of video jobs from HEAD of submit queue
     */
    public List<String> dequeueForSubmit(String model, int maxSize) {
        return jobQueue.lpop(SUBMIT_QUEUE_PREFIX + model, maxSize);
    }

    /**
     * Enqueue a video job to TAIL of syncing queue
     */
    public void enqueueForSync(String videoId) {
        jobQueue.rpush(SYNCING_QUEUE_KEY, videoId);
    }

    /**
     * Dequeue a batch of video jobs from HEAD of syncing queue
     */
    public List<String> dequeueForSync(int maxSize) {
        return jobQueue.lpop(SYNCING_QUEUE_KEY, maxSize);
    }
}
