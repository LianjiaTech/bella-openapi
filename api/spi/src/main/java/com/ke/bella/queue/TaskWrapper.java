package com.ke.bella.queue;

import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.worker.Worker;
import com.theokanning.openai.queue.Task;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class TaskWrapper {
    @Getter
    private final Task task;
    @Setter
    private Worker worker;

    public TaskWrapper(Task task) {
        this.task = task;
    }

    public TaskWrapper(Task task, Worker worker) {
        this.task = task;
        this.worker = worker;
    }

    public static TaskWrapper of(Task task) {
        return new TaskWrapper(task);
    }

    public static TaskWrapper of(Task task, Worker worker) {
        return new TaskWrapper(task, worker);
    }

    public <T> T getPayload(Class<T> targetType) {
        String dataJson = JacksonUtils.serialize(task.getData());
        return JacksonUtils.deserialize(dataJson, targetType);
    }

    public void markComplete(Map<String, Object> result) {
        if("callback".equals(task.getResponseMode()) || "batch".equals(task.getResponseMode())) {
            worker.markComplete(task, result);
        } else {
            emitCompletion(result);
        }
    }

    public void markRetryLater() {
        worker.markRetryLater(task);
    }

    public void emitProgress(String eventId, String eventName, Object eventData) {
        TaskEvent.Progress.Payload payload = new TaskEvent.Progress.Payload(task.getTaskId(), eventId, eventName, eventData);
        Event<TaskEvent.Progress.Payload> event = Event.of(TaskEvent.Progress.NAME, payload);
        worker.emitProgress(task.getInstanceId(), event);
    }

    private void emitCompletion(Map<String, Object> result) {
        TaskEvent.Completion.Payload payload = new TaskEvent.Completion.Payload(task.getTaskId(), result);
        Event<TaskEvent.Completion.Payload> event = Event.of(TaskEvent.Completion.NAME, payload);
        worker.emitCompletion(task.getInstanceId(), event);
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

        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            map.put("name", getName());
            map.put("from", getFrom());
            map.put("payload", JacksonUtils.serialize(getPayload()));
            map.put("context", getContext());
            return map;
        }
    }
}
