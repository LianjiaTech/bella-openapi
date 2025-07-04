package com.ke.bella.openapi.service;

import com.ke.bella.job.queue.config.JobQueueProperties;
import com.ke.bella.job.queue.worker.JobQueueWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JobQueueWorker管理服务
 * 负责创建、启动和管理JobQueueWorker实例
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "bella.job-queue.worker", name = "enabled", havingValue = "true", matchIfMissing = false)
public class JobQueueWorkerManager {

    @Autowired
    private JobQueueProperties jobQueueProperties;

    @Autowired
    private ChannelAwareTaskHandler taskHandler;

    private final Map<String, JobQueueWorker> workers = new ConcurrentHashMap<>();

    @PostConstruct
    public void startWorkers() {
        if (jobQueueProperties.getUrl() == null) {
            log.warn("Job queue URL not configured, workers will not be started");
            return;
        }

        log.info("Starting JobQueueWorkers for job queue URL: {}", jobQueueProperties.getUrl());

        try {
            // Chat completion worker
            createAndStartWorker("chat-completions", "/v1/chat/completions", "gpt-4");
            
            // Audio transcription worker  
            createAndStartWorker("audio-transcriptions", "/v1/audio/transcriptions", "whisper-1");
            
            // Embedding worker
            createAndStartWorker("embeddings", "/v1/embeddings", "text-embedding-ada-002");
            
            log.info("Successfully started {} JobQueueWorkers", workers.size());
            
        } catch (Exception e) {
            log.error("Failed to start JobQueueWorkers", e);
        }
    }

    @PreDestroy
    public void stopWorkers() {
        log.info("Stopping {} JobQueueWorkers", workers.size());
        
        workers.values().forEach(worker -> {
            try {
                worker.stop();
            } catch (Exception e) {
                log.error("Error stopping worker", e);
            }
        });
        
        workers.clear();
        log.info("All JobQueueWorkers stopped");
    }

    private void createAndStartWorker(String workerName, String endpoint, String queueName) {
        try {
            JobQueueWorker worker = new JobQueueWorker(
                jobQueueProperties.getUrl(),
                endpoint,
                queueName
            );
            
            // 配置worker参数
            worker.setTaskHandler(taskHandler);
            worker.setPollSize(getWorkerPollSize());
            worker.setRetryQueueSize(getWorkerRetryQueueSize());
            
            // 启动worker
            worker.start();
            
            workers.put(workerName, worker);
            log.info("Started JobQueueWorker: {} for endpoint: {} queue: {}", workerName, endpoint, queueName);
            
        } catch (Exception e) {
            log.error("Failed to create and start worker: {} for endpoint: {} queue: {}", workerName, endpoint, queueName, e);
        }
    }

    private int getWorkerPollSize() {
        return jobQueueProperties.getWorker().getPollSize();
    }

    private int getWorkerRetryQueueSize() {
        return jobQueueProperties.getWorker().getRetryQueueSize();
    }

    /**
     * 获取指定worker的状态信息
     */
    public Map<String, Object> getWorkerStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("totalWorkers", workers.size());
        status.put("workerNames", workers.keySet());
        status.put("jobQueueUrl", jobQueueProperties.getUrl());
        return status;
    }

    /**
     * 手动停止指定的worker
     */
    public boolean stopWorker(String workerName) {
        JobQueueWorker worker = workers.get(workerName);
        if (worker != null) {
            try {
                worker.stop();
                workers.remove(workerName);
                log.info("Manually stopped worker: {}", workerName);
                return true;
            } catch (Exception e) {
                log.error("Failed to stop worker: {}", workerName, e);
            }
        }
        return false;
    }

    /**
     * 手动启动指定的worker
     */
    public boolean restartWorker(String workerName, String endpoint, String queueName) {
        // 先停止现有worker
        stopWorker(workerName);
        
        // 重新创建并启动
        try {
            createAndStartWorker(workerName, endpoint, queueName);
            return true;
        } catch (Exception e) {
            log.error("Failed to restart worker: {}", workerName, e);
            return false;
        }
    }
}