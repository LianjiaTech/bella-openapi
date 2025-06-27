package com.ke.bella.job.queue.worker;

import com.ke.bella.job.queue.api.entity.response.TaskResp;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Getter;
import lombok.Setter;

public class Task {
    @Getter
    private TaskResp.TaskGetData taskGetData;
    @Setter
    private JobQueueWorker worker;

    public Task(TaskResp.TaskGetData taskGetData) {
        this.taskGetData = taskGetData;
    }

    public Task(TaskResp.TaskGetData taskGetData, JobQueueWorker worker) {
        this.taskGetData = taskGetData;
        this.worker = worker;
    }

    public void markSucceed(String result) {
        worker.markSucceed(getTaskId(), result);
    }

    public void markFailed(String result) {
        worker.markFailed(getTaskId(), result);
    }

    public void markRetryLater() {
        worker.markRetryLater(this);
    }

    public String getTaskId() {
        return taskGetData.getTaskId();
    }

    public String getAk() {
        return taskGetData.getAk();
    }

    public String getStatus() {
        return taskGetData.getStatus();
    }

    public <T> T getPayload(Class<T> targetType) {
        String inputJson = JacksonUtils.serialize(taskGetData.getInputData());
        return JacksonUtils.deserialize(inputJson, targetType);
    }

    public static Task of(TaskResp.TaskGetData task, JobQueueWorker worker) {
        return new Task(task, worker);
    }

    public static Task of(String taskId, JobQueueWorker worker) {
        TaskResp.TaskGetData taskGetData = TaskResp.TaskGetData.builder().taskId(taskId).build();
        return new Task(taskGetData, worker);
    }
}
