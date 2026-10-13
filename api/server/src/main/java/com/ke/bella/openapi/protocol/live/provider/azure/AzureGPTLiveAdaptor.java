package com.ke.bella.openapi.protocol.live.provider.azure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ke.bella.openapi.protocol.live.provider.AbstractLiveSessionAdaptor;
import com.ke.bella.openapi.protocol.live.session.LiveSessionConstants;
import com.ke.bella.openapi.protocol.live.LiveSessionEventType;
import com.ke.bella.openapi.protocol.live.LiveSessionMessage;
import com.ke.bella.openapi.protocol.live.LiveSessionProperty;
import com.ke.bella.openapi.protocol.live.LiveSessionUsage;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class AzureGPTLiveAdaptor extends AbstractLiveSessionAdaptor {
    @Override
    public String endpoint() {
        return LiveSessionConstants.ENDPOINT;
    }

    @Override
    public String getDescription() {
        return "Microsoft Azure GPT Live WebSocket协议";
    }

    @Override
    protected String resolveUrl(String url, LiveSessionProperty property) {
        if(property == null || StringUtils.isBlank(property.getApiVersion())) {
            return url;
        }
        if(url.matches(".*(?:[?&])api-version=[^&]*.*")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "api-version=" + property.getApiVersion();
    }

    @Override
    protected Object toProvider(LiveSessionMessage message, LiveSessionProperty property) {
        ObjectNode provider = JacksonUtils.MAPPER.createObjectNode();
        LiveSessionEventType type = message.eventType();
        if(StringUtils.isNotBlank(message.getEventId())) {
            provider.put("event_id", message.getEventId());
        }
        if(type == LiveSessionEventType.SESSION_START) {
            provider.put("type", "session.start");
            Map<String, Object> config = message.resolveSessionConfig();
            config.remove("capabilities");
            ObjectNode session = JacksonUtils.MAPPER.valueToTree(config);
            String model = property != null && StringUtils.isNotBlank(property.getModel()) ? property.getModel() : message.resolveModel();
            session.put("model", model);
            provider.set("session", session);
            return provider;
        }
        switch (type) {
        case SESSION_UPDATE:
            provider.put("type", "session.update");
            Map<String, Object> config = message.resolveSessionConfig();
            config.remove("capabilities");
            provider.set("session", JacksonUtils.MAPPER.valueToTree(config));
            break;
        case INPUT_AUDIO_APPEND:
            provider.put("type", "session.input_audio.append");
            provider.put("audio", stringValue(message, "audio"));
            break;
        case INPUT_TEXT_APPEND:
            provider.put("type", "session.thinking.append");
            provider.put("content", stringValue(message, "text"));
            putValue(provider, "delegation_id", message.payloadValue("delegation_id"));
            break;
        case TOOL_RESULT:
            provider.put("type", "session.commentary.append");
            provider.put("content", stringValue(message, "content"));
            putValue(provider, "delegation_id", message.payloadValue("delegation_id"));
            break;
        case SESSION_CLOSE:
            provider.put("type", "session.close");
            break;
        default:
            provider.put("type", message.getType());
            if(message.getPayload() != null) {
                message.getPayload().forEach((key, value) -> putValue(provider, key, value));
            }
        }
        return provider;
    }

    @Override
    protected List<LiveSessionMessage> fromProvider(JsonNode provider) {
        String type = provider.path("type").asText();
        LiveSessionMessage message;
        switch (type) {
        case "session.started":
            message = LiveSessionMessage.event(LiveSessionEventType.SESSION_STARTED, provider.path("session").path("id").asText(null));
            message.setSession(map(provider.path("session")));
            break;
        case "session.updated":
            message = LiveSessionMessage.event(LiveSessionEventType.SESSION_UPDATED, null);
            message.setSession(map(provider.path("session")));
            break;
        case "session.input_transcript.delta":
            message = LiveSessionMessage.event(LiveSessionEventType.INPUT_TRANSCRIPT_DELTA, null);
            message.setDelta(value(provider.path("delta")));
            break;
        case "session.output_transcript.delta":
            message = LiveSessionMessage.event(LiveSessionEventType.OUTPUT_TRANSCRIPT_DELTA, null);
            message.setDelta(value(provider.path("delta")));
            break;
        case "session.output_audio.delta":
            message = LiveSessionMessage.event(LiveSessionEventType.OUTPUT_AUDIO_DELTA, null);
            message.setAudio(provider.path("delta").asText());
            break;
        case "session.delegation.created":
            message = LiveSessionMessage.event(LiveSessionEventType.TOOL_CALL, null);
            message.setCall(value(provider.path("delegation")));
            break;
        case "session.closed":
            message = LiveSessionMessage.event(LiveSessionEventType.SESSION_CLOSED, null);
            JsonNode closedUsage = provider.path("usage");
            message.setUsage(usage(closedUsage, closedUsage.isObject() && !closedUsage.isEmpty()));
            message.setSession(map(provider.path("session")));
            break;
        case "session.usage.updated":
            message = LiveSessionMessage.event(LiveSessionEventType.USAGE_UPDATED, null);
            message.setUsage(usage(provider.path("usage"), false));
            break;
        case "error":
            message = LiveSessionMessage.error(null, provider.path("error").path("code").asText("provider_error"),
                    provider.path("error").path("message").asText("Azure Live error"));
            break;
        default:
            return Collections.emptyList();
        }
        return Collections.singletonList(message);
    }

    private LiveSessionUsage usage(JsonNode usage, boolean confirmed) {
        LiveSessionUsage converted = new LiveSessionUsage();
        JsonNode inputDetails = usage.has("input_token_details") ? usage.path("input_token_details") : usage.path("input_tokens_details");
        JsonNode outputDetails = usage.has("output_token_details") ? usage.path("output_token_details") : usage.path("output_tokens_details");
        converted.setInputTextTokens(firstLong(usage, "input_text_tokens", inputDetails, "text_tokens"));
        converted.setInputAudioTokens(firstLong(usage, "input_audio_tokens", inputDetails, "audio_tokens"));
        converted.setInputVideoTokens(firstLong(usage, "input_video_tokens", inputDetails, "image_tokens"));
        converted.setOutputTextTokens(firstLong(usage, "output_text_tokens", outputDetails, "text_tokens"));
        converted.setOutputAudioTokens(firstLong(usage, "output_audio_tokens", outputDetails, "audio_tokens"));
        converted.setCachedTokens(firstLong(usage, "cached_tokens", inputDetails, "cached_tokens"));
        converted.setToolCalls(longValue(usage, "tool_calls"));
        Long duration = longValue(usage, "session_duration_seconds");
        if(duration == null) {
            duration = longValue(usage, "seconds");
        }
        converted.setSessionDurationSeconds(duration);
        converted.setFinalUsageConfirmed(confirmed);
        return converted;
    }

    private Long firstLong(JsonNode root, String rootField, JsonNode details, String detailsField) {
        Long value = longValue(root, rootField);
        return value == null ? longValue(details, detailsField) : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(JsonNode node) {
        return node == null || !node.isObject() ? null : JacksonUtils.MAPPER.convertValue(node, Map.class);
    }

    private Long longValue(JsonNode node, String field) {
        return node.has(field) ? node.path(field).asLong() : null;
    }

    private Object value(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : JacksonUtils.MAPPER.convertValue(node, Object.class);
    }

    private String stringValue(LiveSessionMessage message, String key) {
        Object value = message.payloadValue(key);
        return value == null ? "" : String.valueOf(value);
    }

    private void putValue(ObjectNode target, String key, Object value) {
        if(value != null) {
            target.set(key, JacksonUtils.MAPPER.valueToTree(value));
        }
    }

}
