package com.ke.bella.openapi.task;

import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Set;

/**
 * 通用的回调任务服务抽象类
 * 提供基于Redis ZSet的延迟任务处理能力，支持任务完成检查和回调处理
 * 
 * @param <T> 任务数据类型，必须实现TaskData接口
 */
@Slf4j
public abstract class CallbackTaskService<T extends TaskData> {

    private static final Logger logger = LoggerFactory.getLogger(CallbackTaskService.class);

    @Autowired
    protected RedisTemplate<String, String> redisTemplate;

    /**
     * 获取Redis ZSet的键名
     * 子类需要实现此方法来提供不同的键名以区分不同类型的任务
     * 
     * @return Redis ZSet键名
     */
    protected abstract String getZSetKey();

    /**
     * 获取任务数据的Class类型
     * 用于JSON反序列化
     * 
     * @return 任务数据的Class对象
     */
    protected abstract Class<T> getTaskDataClass();

    /**
     * 获取任务完成回调处理器
     * 
     * @return 任务完成回调处理器
     */
    protected abstract TaskCompletionCallback<T> getTaskCompletionCallback();

    /**
     * 添加延迟执行的任务
     * 
     * @param taskData 任务数据
     */
    public void addTask(T taskData) {
        addTask(taskData, getDefaultDelayMillis(), getDefaultMaxRetries());
    }

    /**
     * 添加延迟执行的任务
     * 
     * @param taskData    任务数据
     * @param delayMillis 延迟执行时间（毫秒）
     */
    public void addTask(T taskData, long delayMillis) {
        addTask(taskData, delayMillis, getDefaultMaxRetries());
    }

    /**
     * 添加延迟执行的任务带指定重试次数
     * 
     * @param taskData    任务数据
     * @param delayMillis 延迟执行时间（毫秒）
     * @param maxRetries  最大重试次数
     */
    public void addTask(T taskData, long delayMillis, int maxRetries) {
        long executeTime = System.currentTimeMillis() + delayMillis;
        taskData.setTimestamp(executeTime);
        taskData.setRemainingRetries(maxRetries);

        String taskJson = JacksonUtils.serialize(taskData);
        ZSetOperations<String, String> zsetOps = redisTemplate.opsForZSet();
        zsetOps.add(getZSetKey(), taskJson, executeTime);

        logger.info("Added callback task with ID: {}, execute time: {}, max retries: {}",
                taskData.getTaskId(), executeTime, maxRetries);
    }

    /**
     * 获取默认延迟执行时间（毫秒）
     * 子类可以重写此方法来自定义延迟执行时间（毫秒）
     * 
     * @return 默认延迟执行时间（毫秒）
     */
    protected int getDefaultDelayMillis() {
        return 30000;
    }

    /**
     * 获取默认最大重试次数
     * 子类可以重写此方法来自定义重试次数
     * 
     * @return 默认最大重试次数
     */
    protected int getDefaultMaxRetries() {
        return 5;
    }

    /**
     * 定时处理任务，每5秒执行一次
     * 可以通过重写此方法来自定义执行频率
     */
    @Scheduled(fixedRate = 5000)
    public void processCallbackTasks() {
        ZSetOperations<String, String> zsetOps = redisTemplate.opsForZSet();
        TaskCompletionCallback<T> callback = getTaskCompletionCallback();
        long currentTime = System.currentTimeMillis();
        String zsetKey = getZSetKey();

        while (true) {
            // 获取最小score的任务
            Set<ZSetOperations.TypedTuple<String>> tasks = zsetOps.rangeWithScores(zsetKey, 0, 0);
            if(tasks == null || tasks.isEmpty()) {
                break; // 队列为空
            }

            ZSetOperations.TypedTuple<String> taskTuple = tasks.iterator().next();
            String taskJson = taskTuple.getValue();
            Double score = taskTuple.getScore();

            if(taskJson == null || score == null) {
                break;
            }

            // 原子删除操作
            Long removed = zsetOps.remove(zsetKey, taskJson);
            if(removed == null || removed == 0) {
                // 任务已被其他实例处理，继续下一个
                continue;
            }

            try {
                T taskData = JacksonUtils.deserialize(taskJson, getTaskDataClass());
                if(taskData == null) {
                    logger.warn("Failed to deserialize task data, skipping");
                    continue;
                }

                // 检查任务执行时间是否到了
                if(taskData.getTimestamp() > currentTime) {
                    // 任务还没到执行时间，重新放回去并退出
                    zsetOps.add(zsetKey, taskJson, taskData.getTimestamp());
                    break;
                }

                // 检查任务是否已完成
                boolean isCompleted = callback.isTaskCompleted(taskData);

                if(isCompleted) {
                    // 任务已完成，执行回调处理
                    boolean callbackSuccess = callback.onTaskCompleted(taskData);

                    if(callbackSuccess) {
                        // 回调处理成功，任务完成
                        logger.info("Successfully processed completed task with ID: {}", taskData.getTaskId());
                    } else {
                        // 回调处理失败
                        handleRetryOrFail(taskData, callback.getRetryInterval(), zsetKey,
                                "Callback processing failed for task ID: " + taskData.getTaskId(),
                                "Task callback processing failed after all retries - task ID: " + taskData.getTaskId());
                    }
                } else {
                    // 任务仍在处理中
                    handleRetryOrFail(taskData, callback.getRetryInterval(), zsetKey,
                            "Task still processing, task ID: " + taskData.getTaskId(),
                            "Task processing timeout after all retries - task ID: " + taskData.getTaskId());
                }

            } catch (Exception e) {
                // 处理异常
                T taskData = JacksonUtils.deserialize(taskJson, getTaskDataClass());
                if(taskData != null) {
                    handleRetryOrFail(taskData, getTaskCompletionCallback().getRetryInterval(), zsetKey,
                            "Exception occurred while processing task ID: " + taskData.getTaskId() + ", error: " + e.getMessage(),
                            "Task processing failed after all retries due to exception - task ID: " + taskData.getTaskId() + ", error: "
                                    + e.getMessage());
                } else {
                    logger.error("Failed to deserialize task data for retry, discarding task. Error: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 处理重试或失败逻辑
     * 
     * @param taskData      任务数据
     * @param retryInterval 重试间隔
     * @param zsetKey       Redis ZSet键名
     * @param retryMessage  重试日志消息
     * @param failMessage   失败日志消息
     */
    private void handleRetryOrFail(T taskData, long retryInterval, String zsetKey,
            String retryMessage, String failMessage) {
        if(taskData.getRemainingRetries() > 0) {
            taskData.decrementRetries();
            scheduleRetry(taskData, retryInterval, zsetKey,
                    retryMessage + ", remaining retries: " + taskData.getRemainingRetries());
        } else {
            logger.error(failMessage);
        }
    }

    /**
     * 重新调度任务执行
     * 
     * @param taskData      任务数据
     * @param retryInterval 重试间隔
     * @param zsetKey       Redis ZSet键名
     * @param logMessage    日志消息
     */
    private void scheduleRetry(T taskData, long retryInterval, String zsetKey, String logMessage) {
        taskData.setTimestamp(System.currentTimeMillis() + retryInterval);
        String newTaskJson = JacksonUtils.serialize(taskData);
        ZSetOperations<String, String> zsetOps = redisTemplate.opsForZSet();
        zsetOps.add(zsetKey, newTaskJson, taskData.getTimestamp());
        logger.debug("{}, will retry in {}ms", logMessage, retryInterval);
    }

    /**
     * 获取当前队列中的任务数量
     * 
     * @return 任务数量
     */
    public long getTaskCount() {
        ZSetOperations<String, String> zsetOps = redisTemplate.opsForZSet();
        Long count = zsetOps.count(getZSetKey(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return count != null ? count : 0;
    }

    /**
     * 清空所有任务
     */
    public void clearAllTasks() {
        redisTemplate.delete(getZSetKey());
        logger.info("Cleared all tasks from queue: {}", getZSetKey());
    }
}
