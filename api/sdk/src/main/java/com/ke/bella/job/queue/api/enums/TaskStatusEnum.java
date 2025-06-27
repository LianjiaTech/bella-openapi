package com.ke.bella.job.queue.api.enums;

import lombok.Getter;

@Getter
public enum TaskStatusEnum {
    VALIDATING(0, "validating"),
    FAILED(1, "failed"),
    IN_PROGRESS(2, "in_progress"),
    FINALIZING(3, "finalizing"),
    COMPLETED(4, "completed"),
    EXPIRED(5, "expired"),
    CANCELLING(6, "cancelling"),
    CANCELLED(7, "cancelled");

    private final Integer code;
    private final String description;

    TaskStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static TaskStatusEnum find(String status) {
        for (TaskStatusEnum taskStatus : TaskStatusEnum.values()) {
            if(taskStatus.getDescription().equals(status)) {
                return taskStatus;
            }
        }
        throw new RuntimeException("Invalid task status: " + status);
    }

    public static TaskStatusEnum find(int code) {
        for (TaskStatusEnum status : TaskStatusEnum.values()) {
            if(status.getCode() == code) {
                return status;
            }
        }
        throw new RuntimeException("Invalid task status code: " + code);
    }
}
