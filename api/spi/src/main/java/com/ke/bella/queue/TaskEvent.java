package com.ke.bella.queue;

import com.google.common.collect.Maps;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.MapUtils;

import java.util.Map;

@SuppressWarnings("all")
public class TaskEvent {

    public static class Progress {

        public final static String NAME = "task-progress-event";

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Payload {
            private String taskId;
            private String eventId;
            private String eventName;
            private Object eventData;
        }

        public static Payload fromPayload(String payload) {
            Map<String, Object> payloadMap = JacksonUtils.toMap(payload);

            String taskId = MapUtils.getString(payloadMap, "taskId");
            String eventId = MapUtils.getString(payloadMap, "eventId");
            String eventName = MapUtils.getString(payloadMap, "eventName");
            Object eventData = MapUtils.getObject(payloadMap, "eventData");

            return Payload.builder()
                    .taskId(taskId)
                    .eventId(eventId)
                    .eventName(eventName)
                    .eventData(eventData)
                    .build();
        }
    }

    public static class Completion {

        public final static String NAME = "task-completion-event";

        @Data
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Payload {
            private String taskId;
            private Map<String, Object> result;
        }

        public static Payload fromPayload(String payload) {
            Map<String, Object> payloadMap = JacksonUtils.toMap(payload);
            String taskId = MapUtils.getString(payloadMap, "taskId");
            Map result = MapUtils.getMap(payloadMap, "result", Maps.newHashMap());

            return Payload.builder()
                    .taskId(taskId)
                    .result(result)
                    .build();
        }
    }

}
