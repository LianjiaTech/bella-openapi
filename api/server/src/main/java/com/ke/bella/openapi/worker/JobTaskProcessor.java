package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.service.JobQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Job任务处理器
 * 负责异步执行从job queue拉取的任务
 */
@Component
@Slf4j
public class JobTaskProcessor {
    
    @Autowired
    private AdaptorManager adaptorManager;
    
    @Autowired
    private JobQueueService jobQueueService;
    
    // 每个channel的当前并发任务数
    private final ConcurrentHashMap<String, AtomicInteger> channelConcurrency = new ConcurrentHashMap<>();
    
    // 任务执行线程池
    private final ExecutorService taskExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "job-task-executor");
        t.setDaemon(true);
        return t;
    });
    
    /**
     * 提交任务到处理器
     */
    public CompletableFuture<Void> submitTask(String channelCode, JobTask task) {
        // 增加channel并发计数
        channelConcurrency.computeIfAbsent(channelCode, k -> new AtomicInteger(0)).incrementAndGet();
        
        return CompletableFuture.runAsync(() -> {
            try {
                processTask(channelCode, task);
            } finally {
                // 减少channel并发计数
                AtomicInteger counter = channelConcurrency.get(channelCode);
                if (counter != null) {
                    counter.decrementAndGet();
                }
            }
        }, taskExecutor);
    }
    
    /**
     * 处理单个任务
     */
    private void processTask(String channelCode, JobTask task) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Processing job task {} for channel {}", task.getTaskId(), channelCode);
            
            // 创建EndpointProcessData
            EndpointProcessData processData = createProcessData(task);
            processData.setChannelCode(channelCode);
            processData.setStartTime(startTime);
            
            // 获取对应的protocol adaptor
            IProtocolAdaptor adaptor = adaptorManager.getAdaptor(task.getEndpoint(), channelCode);
            if (adaptor == null) {
                throw new RuntimeException("No adaptor found for endpoint: " + task.getEndpoint() + ", channel: " + channelCode);
            }
            
            // 执行任务
            OpenapiResponse response = adaptor.process(processData);
            
            // 处理结果
            handleTaskResult(task, response, null);
            
            log.info("Successfully processed job task {} for channel {} in {}ms", 
                task.getTaskId(), channelCode, System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            log.error("Error processing job task {} for channel {}", task.getTaskId(), channelCode, e);
            handleTaskResult(task, null, e);
        }
    }
    
    /**
     * 创建EndpointProcessData
     */
    private EndpointProcessData createProcessData(JobTask task) {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint(task.getEndpoint());
        processData.setChannelCode(task.getChannelCode());
        processData.setRequestData(task.getTaskData());
        processData.setApiKey(task.getApiKey());
        
        // 设置其他必要的属性
        processData.setInnerLog(true);
        
        return processData;
    }
    
    /**
     * 处理任务结果
     */
    private void handleTaskResult(JobTask task, OpenapiResponse response, Exception error) {
        try {
            if (error != null) {
                // 错误处理：可能需要重试或记录失败
                if (shouldRetry(task, error)) {
                    retryTask(task);
                } else {
                    recordTaskFailure(task, error);
                }
            } else {
                // 成功处理：记录结果
                recordTaskSuccess(task, response);
            }
            
        } catch (Exception e) {
            log.error("Error handling task result for task {}", task.getTaskId(), e);
        }
    }
    
    /**
     * 判断是否应该重试任务
     */
    private boolean shouldRetry(JobTask task, Exception error) {
        if (task.getRetryCount() == null) {
            task.setRetryCount(0);
        }
        
        if (task.getMaxRetries() == null) {
            task.setMaxRetries(3); // 默认最大重试3次
        }
        
        return task.getRetryCount() < task.getMaxRetries();
    }
    
    /**
     * 重试任务
     */
    private void retryTask(JobTask task) {
        task.setRetryCount(task.getRetryCount() + 1);
        log.info("Retrying task {} (attempt {}/{})", task.getTaskId(), task.getRetryCount(), task.getMaxRetries());
        
        // 重新提交到队列或延迟重试
        // 这里需要根据实际的job queue实现来调整
    }
    
    /**
     * 记录任务失败
     */
    private void recordTaskFailure(JobTask task, Exception error) {
        log.error("Task {} failed after {} retries: {}", task.getTaskId(), task.getRetryCount(), error.getMessage());
        // 这里可以记录到数据库或发送通知
    }
    
    /**
     * 记录任务成功
     */
    private void recordTaskSuccess(JobTask task, OpenapiResponse response) {
        log.info("Task {} completed successfully", task.getTaskId());
        // 这里可以将结果存储到job queue结果存储中
    }
    
    /**
     * 获取指定channel的当前并发数
     */
    public int getCurrentConcurrency(String channelCode) {
        AtomicInteger counter = channelConcurrency.get(channelCode);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * 获取所有可用的channel codes
     */
    public Collection<String> getAllAvailableChannelCodes() {
        // 这里需要根据实际的channel管理实现
        // 暂时返回空列表，实际应该从数据库或配置中获取
        return List.of();
    }
}