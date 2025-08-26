package com.ke.bella.queue;

import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.remote.QueueOps;
import com.ke.bella.queue.remote.TaskEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class Task {
    @Getter
    private final QueueOps.Task task;
    @Setter
    private Worker worker;

    public Task(QueueOps.Task task) {
        this.task = task;
    }

    public Task(QueueOps.Task task, Worker worker) {
        this.task = task;
        this.worker = worker;
    }

    public static Task of(QueueOps.Task task) {
        return new Task(task);
    }

    public static Task of(QueueOps.Task task, Worker worker) {
        return new Task(task, worker);
    }

    public <T> T getPayload(Class<T> targetType) {
        String dataJson = JacksonUtils.serialize(task.getData());
        return JacksonUtils.deserialize(dataJson, targetType);
    }

    public void markComplete(Object result) {
        worker.markComplete(task, result);
    }

    public void markRetryLater() {
        worker.markRetryLater(task);
    }

    public Event<TaskEvent.Progress.Payload> emitProgress(String eventId, String eventName, Object eventData) {
        TaskEvent.Progress.Payload payload = new TaskEvent.Progress.Payload(task.getTaskId(), eventId, eventName, eventData);
        return Event.of(TaskEvent.Progress.NAME, payload);
    }

    public Event<TaskEvent.Completion.Payload> emitCompletion(Map<String, Object> result) {
        TaskEvent.Completion.Payload payload = new TaskEvent.Completion.Payload(task.getTaskId(), result);
        return Event.of(TaskEvent.Completion.NAME, payload);
    }

    @Data
    @AllArgsConstructor
    public static class Event<T> {
        String name;
        String from;
        T payload;
        String context;

        public static <T> Event<T> of(String name, T payload) {
            return new Event<>(name, StringUtils.EMPTY, payload, StringUtils.EMPTY);
        }
    }
}
