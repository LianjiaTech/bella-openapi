package com.ke.bella.openapi.task;

/**
 * 任务数据接口，用于定义延迟执行任务的数据结构
 */
public interface TaskData {

    /**
     * 获取任务的执行时间戳
     * 
     * @return 执行时间戳（毫秒）
     */
    long getTimestamp();

    /**
     * 设置任务的执行时间戳
     * 
     * @param timestamp 执行时间戳（毫秒）
     */
    void setTimestamp(long timestamp);

    /**
     * 获取任务的唯一标识
     * 
     * @return 任务唯一标识
     */
    String getTaskId();

    /**
     * 获取剩余重试次数
     * 
     * @return 剩余重试次数
     */
    int getRemainingRetries();

    /**
     * 设置剩余重试次数
     * 
     * @param remainingRetries 剩余重试次数
     */
    void setRemainingRetries(int remainingRetries);

    /**
     * 减少一次重试次数
     * 
     * @return 减少后的剩余重试次数
     */
    default int decrementRetries() {
        int current = getRemainingRetries();
        int newValue = Math.max(0, current - 1);
        setRemainingRetries(newValue);
        return newValue;
    }
}
