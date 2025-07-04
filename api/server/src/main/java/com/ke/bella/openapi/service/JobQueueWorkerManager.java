package com.ke.bella.openapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.job.queue.JobQueueClient;
import com.ke.bella.job.queue.config.JobQueueProperties;
import com.ke.bella.job.queue.worker.JobQueueWorker;
import com.ke.bella.openapi.db.repo.ModelRepo;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "bella.job-queue.worker.enabled", havingValue = "true")
@Slf4j
public class JobQueueWorkerManager implements ApplicationListener<ContextRefreshedEvent> {
    
    @Autowired
    private ModelRepo modelRepository;
    
    @Autowired
    private JobQueueProperties jobQueueProperties;
    
    @Autowired
    private ModelAwareTaskHandler taskHandler;
    
    private final Map<String, JobQueueWorker> workers = new ConcurrentHashMap<>();
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() == null) { // 只在根应用上下文启动时执行
            startWorkersBasedOnModelConfig();
        }
    }
    
    private void startWorkersBasedOnModelConfig() {
        try {
            log.info("Starting JobQueue workers based on model configuration...");
            
            List<ModelDB> models = modelRepository.listAll();
            
            for (ModelDB model : models) {
                if (shouldStartWorkerForModel(model)) {
                    startWorkerForModel(model);
                }
            }
            
            log.info("JobQueue workers started. Total workers: {}", workers.size());
            
        } catch (Exception e) {
            log.error("Failed to start JobQueue workers", e);
        }
    }
    
    private boolean shouldStartWorkerForModel(ModelDB model) {
        if (model.getProperties() == null) {
            return false;
        }
        
        try {
            Map<String, Object> properties = JacksonUtils.deserialize(
                    model.getProperties(), new TypeReference<Map<String, Object>>() {});
            
            // 检查是否配置了JobQueue相关属性
            Object queueName = properties.get("queueName");
            Object jobQueueWorker = properties.get("jobQueueWorker");
            
            return queueName != null && StringUtils.isNotBlank(queueName.toString()) &&
                   (jobQueueWorker == null || Boolean.parseBoolean(jobQueueWorker.toString()));
                   
        } catch (Exception e) {
            log.warn("Failed to parse model properties for model: {}", model.getModelName(), e);
            return false;
        }
    }
    
    private void startWorkerForModel(ModelDB model) {
        try {
            String modelName = model.getModelName();
            
            if (workers.containsKey(modelName)) {
                log.warn("Worker for model {} already exists", modelName);
                return;
            }
            
            Map<String, Object> properties = JacksonUtils.deserialize(
                    model.getProperties(), new TypeReference<Map<String, Object>>() {});
            
            String queueName = properties.get("queueName").toString();
            
            // 创建JobQueueClient
            JobQueueClient client = JobQueueClient.getInstance(jobQueueProperties.getUrl());
            
            // 创建Worker配置
            JobQueueWorker.Config config = new JobQueueWorker.Config();
            config.setQueueName(queueName);
            config.setPollSize(getIntProperty(properties, "pollSize", 10));
            config.setPollInterval(getIntProperty(properties, "pollInterval", 5000));
            config.setRetryQueueSize(getIntProperty(properties, "retryQueueSize", 1000));
            config.setThreadPoolSize(getIntProperty(properties, "threadPoolSize", 2));
            
            // 创建并启动Worker
            JobQueueWorker worker = new JobQueueWorker(config, client, 
                    task -> taskHandler.handleTask(task, modelName));
            
            worker.start();
            workers.put(modelName, worker);
            
            log.info("Started JobQueue worker for model: {}, queue: {}", modelName, queueName);
            
        } catch (Exception e) {
            log.error("Failed to start worker for model: {}", model.getModelName(), e);
        }
    }
    
    private int getIntProperty(Map<String, Object> properties, String key, int defaultValue) {
        Object value = properties.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid integer property {}: {}, using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down JobQueue workers...");
        
        for (Map.Entry<String, JobQueueWorker> entry : workers.entrySet()) {
            try {
                entry.getValue().stop();
                log.info("Stopped worker for model: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Failed to stop worker for model: {}", entry.getKey(), e);
            }
        }
        
        workers.clear();
        log.info("All JobQueue workers stopped");
    }
    
    public Map<String, String> getWorkerStatus() {
        Map<String, String> status = new HashMap<>();
        
        for (Map.Entry<String, JobQueueWorker> entry : workers.entrySet()) {
            JobQueueWorker worker = entry.getValue();
            status.put(entry.getKey(), worker.isRunning() ? "RUNNING" : "STOPPED");
        }
        
        return status;
    }
}