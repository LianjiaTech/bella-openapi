package com.ke.bella.openapi.task;

/**
 * 任务完成回调接口
 * @param <T> 任务数据的具体类型
 */
public interface TaskCompletionCallback<T extends TaskData> {
    
    /**
     * 检查任务是否已完成
     * @param taskData 任务数据
     * @return 任务完成状态：true表示已完成，false表示仍在处理中
     * @throws Exception 检查过程中的异常
     */
    boolean isTaskCompleted(T taskData) throws Exception;
    
    /**
     * 任务完成后的回调处理
     * @param taskData 任务数据
     * @return 处理结果：true表示处理成功，false表示需要重试
     * @throws Exception 处理过程中的异常
     */
    boolean onTaskCompleted(T taskData) throws Exception;
    
    /**
     * 获取重试间隔时间（毫秒）
     * @return 重试间隔时间，默认10秒
     */
    default long getRetryInterval() {
        return 10000L;
    }
}
