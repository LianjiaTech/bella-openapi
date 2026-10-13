package com.ke.bella.openapi.protocol.live;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LiveSessionMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type;
    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("session_id")
    private String sessionId;
    private String model;
    private Map<String, Object> session;
    private Map<String, Object> setup;
    private LiveModelFeatures capabilities;
    private LiveSessionUsage usage;
    private String audio;
    private String video;
    private String text;
    @JsonProperty("mime_type")
    private String mimeType;
    private Object delta;
    private Object content;
    private Object call;
    private Object cancellation;
    private Object resumption;
    @JsonProperty("go_away")
    private Object goAway;
    @JsonProperty("delegation_id")
    private Object delegationId;
    @JsonProperty("call_id")
    private String callId;
    private String name;
    private Object output;
    private Object response;
    @JsonProperty("function_responses")
    private Object functionResponses;
    @JsonProperty("turn_complete")
    private Boolean turnComplete;
    @JsonProperty("resume_session_id")
    private String resumeSessionId;
    @JsonAlias("data")
    private Map<String, Object> payload;
    private Error error;

    public LiveSessionEventType eventType() {
        return LiveSessionEventType.fromString(type);
    }

    @JsonIgnore
    public String resolveModel() {
        if(model != null && !model.trim().isEmpty()) {
            return model;
        }
        Object configured = firstValue("model", session, setup, payload);
        return configured == null ? null : String.valueOf(configured);
    }

    @JsonIgnore
    public Map<String, Object> resolveSessionConfig() {
        Map<String, Object> configured = session != null ? session : setup != null ? setup : payload;
        return configured == null ? new LinkedHashMap<>() : new LinkedHashMap<>(configured);
    }

    @JsonIgnore
    public Object payloadValue(String key) {
        if("audio".equals(key) && audio != null) {
            return audio;
        }
        if("video".equals(key) && video != null) {
            return video;
        }
        if("text".equals(key) && text != null) {
            return text;
        }
        if("mime_type".equals(key) && mimeType != null) {
            return mimeType;
        }
        if("delta".equals(key) && delta != null) {
            return delta;
        }
        if("content".equals(key) && content != null) {
            return content;
        }
        if("call".equals(key) && call != null) {
            return call;
        }
        if("cancellation".equals(key) && cancellation != null) {
            return cancellation;
        }
        if("resumption".equals(key) && resumption != null) {
            return resumption;
        }
        if("go_away".equals(key) && goAway != null) {
            return goAway;
        }
        if("delegation_id".equals(key) && delegationId != null) {
            return delegationId;
        }
        if("call_id".equals(key) && callId != null) {
            return callId;
        }
        if("name".equals(key) && name != null) {
            return name;
        }
        if("output".equals(key) && output != null) {
            return output;
        }
        if("response".equals(key) && response != null) {
            return response;
        }
        if("function_responses".equals(key) && functionResponses != null) {
            return functionResponses;
        }
        if("turn_complete".equals(key) && turnComplete != null) {
            return turnComplete;
        }
        if("resume_session_id".equals(key) && resumeSessionId != null) {
            return resumeSessionId;
        }
        if(payload != null && payload.containsKey(key)) {
            return payload.get(key);
        }
        Map<String, Object> configured = session != null ? session : setup;
        return configured == null ? null : configured.get(key);
    }

    private Object firstValue(String key, Map<String, Object>... sources) {
        for (Map<String, Object> source : sources) {
            if(source != null && source.get(key) != null) {
                return source.get(key);
            }
        }
        return null;
    }

    public static LiveSessionMessage event(LiveSessionEventType type, String sessionId) {
        LiveSessionMessage message = new LiveSessionMessage();
        message.setType(type.value());
        message.setSessionId(sessionId);
        return message;
    }

    public static LiveSessionMessage error(String sessionId, String code, String message) {
        LiveSessionMessage response = event(LiveSessionEventType.ERROR, sessionId);
        response.setError(new Error(code, message));
        return response;
    }

    @Data
    public static class Error implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final String message;
    }
}
