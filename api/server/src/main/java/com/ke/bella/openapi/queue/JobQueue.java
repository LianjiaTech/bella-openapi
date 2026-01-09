package com.ke.bella.openapi.queue;

import java.util.List;

/**
 * Generic job queue interface for distributed task processing.
 * Supports any type of job identifier (typically String ID).
 * Uses Redis deque semantics: LEFT=HEAD, RIGHT=TAIL
 *
 * @param <T> Type of job identifier
 */
public interface JobQueue<T> {

    /**
     * Push job to HEAD (left) - for priority/retry tasks
     * 
     * @param queueKey Queue identifier
     * @param jobId    Job identifier
     */
    void lpush(String queueKey, T jobId);

    /**
     * Push job to TAIL (right) - for new tasks (FIFO)
     * 
     * @param queueKey Queue identifier
     * @param jobId    Job identifier
     */
    void rpush(String queueKey, T jobId);

    /**
     * Pop jobs from HEAD (left) - standard dequeue
     * 
     * @param queueKey Queue identifier
     * @param maxSize  Maximum number of jobs to pop
     * 
     * @return List of job identifiers
     */
    List<T> lpop(String queueKey, int maxSize);
}
