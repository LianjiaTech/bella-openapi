package com.ke.bella.openapi.protocol.live.provider.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class GeminiLiveAdaptor extends AbstractLiveSessionAdaptor {
    @Override
    public String endpoint() {
        return LiveSessionConstants.ENDPOINT;
    }

    @Override
    public String getDescription() {
        return "Google Gemini Live WebSocket协议";
    }

    @Override
    protected Object toProvider(LiveSessionMessage message, LiveSessionProperty property) {
        ObjectNode provider = JacksonUtils.MAPPER.createObjectNode();
        LiveSessionEventType type = message.eventType();
        if(type == LiveSessionEventType.SESSION_START || type == LiveSessionEventType.SETUP) {
            provider.set("setup", setup(message, property));
            return provider;
        }
        switch (type) {
        case INPUT_AUDIO_APPEND:
            provider.putObject("realtimeInput").putArray("mediaChunks").addObject()
                    .put("mimeType", stringValue(message, "mime_type", "audio/pcm"))
                    .put("data", stringValue(message, "audio", ""));
            break;
        case INPUT_VIDEO_APPEND:
            provider.putObject("realtimeInput").putArray("mediaChunks").addObject()
                    .put("mimeType", stringValue(message, "mime_type", "image/jpeg"))
                    .put("data", stringValue(message, "video", ""));
            break;
        case INPUT_TEXT_APPEND:
            ObjectNode clientContent = provider.putObject("clientContent");
            clientContent.putArray("turns").addObject()
                    .put("role", "user").putArray("parts").addObject()
                    .put("text", stringValue(message, "text", ""));
            clientContent.put("turnComplete", booleanValue(message.payloadValue("turn_complete"), false));
            break;
        case TOOL_RESULT:
            provider.putObject("toolResponse").set("functionResponses", functionResponses(message));
            break;
        case SESSION_CLOSE:
            provider.putObject("clientContent").put("turnComplete", true);
            break;
        default:
            if(message.getPayload() != null) {
                provider.set(message.getType(), JacksonUtils.MAPPER.valueToTree(message.getPayload()));
            }
        }
        return provider;
    }

    private ObjectNode setup(LiveSessionMessage message, LiveSessionProperty property) {
        Map<String, Object> config = message.resolveSessionConfig();
        ObjectNode setup = JacksonUtils.MAPPER.createObjectNode();
        String model = property != null && StringUtils.isNotBlank(property.getModel()) ? property.getModel() : message.resolveModel();
        setup.put("model", model);

        copy(config, setup, "generationConfig", "generation_config");
        copy(config, setup, "systemInstruction", "system_instruction");
        copy(config, setup, "tools");
        copy(config, setup, "realtimeInputConfig", "realtime_input_config");
        copy(config, setup, "sessionResumption", "session_resumption");
        copy(config, setup, "contextWindowCompression", "context_window_compression");
        copy(config, setup, "inputAudioTranscription", "input_audio_transcription");
        copy(config, setup, "outputAudioTranscription", "output_audio_transcription");
        copy(config, setup, "proactivity");
        copy(config, setup, "affectiveDialog", "affective_dialog");

        Object instructions = first(config, "instructions");
        if(!setup.has("systemInstruction") && instructions != null) {
            setup.putObject("systemInstruction").putArray("parts").addObject().put("text", String.valueOf(instructions));
        }

        ObjectNode generationConfig = setup.has("generationConfig") && setup.get("generationConfig").isObject()
                ? (ObjectNode) setup.get("generationConfig") : setup.putObject("generationConfig");
        Object modalities = first(config, "output_modalities", "modalities");
        if(modalities != null && !generationConfig.has("responseModalities")) {
            ArrayNode responseModalities = generationConfig.putArray("responseModalities");
            JsonNode values = JacksonUtils.MAPPER.valueToTree(modalities);
            if(values.isArray()) {
                values.forEach(value -> responseModalities.add(value.asText().toUpperCase(Locale.ROOT)));
            } else {
                responseModalities.add(String.valueOf(modalities).toUpperCase(Locale.ROOT));
            }
        }
        Object voice = first(config, "voice");
        if(voice != null && !generationConfig.has("speechConfig")) {
            generationConfig.putObject("speechConfig").putObject("voiceConfig").putObject("prebuiltVoiceConfig")
                    .put("voiceName", String.valueOf(voice));
        }
        if(generationConfig.isEmpty()) {
            setup.remove("generationConfig");
        }
        return setup;
    }

    private JsonNode functionResponses(LiveSessionMessage message) {
        Object responses = message.payloadValue("function_responses");
        if(responses != null) {
            JsonNode converted = JacksonUtils.MAPPER.valueToTree(responses);
            return converted.isArray() ? converted : JacksonUtils.MAPPER.createArrayNode().add(converted);
        }
        ObjectNode response = JacksonUtils.MAPPER.createObjectNode();
        put(response, "id", message.payloadValue("call_id"));
        put(response, "name", message.payloadValue("name"));
        Object result = message.payloadValue("response");
        if(result == null) {
            result = message.payloadValue("content");
        }
        if(result == null) {
            result = message.payloadValue("output");
        }
        if(result instanceof Map) {
            response.set("response", JacksonUtils.MAPPER.valueToTree(result));
        } else if(result != null) {
            response.putObject("response").put("result", String.valueOf(result));
        }
        return JacksonUtils.MAPPER.createArrayNode().add(response);
    }

    @Override
    protected List<LiveSessionMessage> fromProvider(JsonNode provider) {
        if(provider == null || provider.isNull()) {
            return Collections.emptyList();
        }
        List<LiveSessionMessage> messages = new ArrayList<>();
        if(provider.has("error")) {
            messages.add(LiveSessionMessage.error(null, provider.path("error").path("code").asText("provider_error"),
                    provider.path("error").path("message").asText("Gemini Live error")));
        }
        if(provider.has("setupComplete")) {
            messages.add(LiveSessionMessage.event(LiveSessionEventType.SESSION_STARTED, null));
        }
        if(provider.has("toolCall")) {
            LiveSessionMessage message = LiveSessionMessage.event(LiveSessionEventType.TOOL_CALL, null);
            message.setCall(value(provider.get("toolCall")));
            messages.add(message);
        }
        if(provider.has("toolCallCancellation")) {
            LiveSessionMessage message = LiveSessionMessage.event(LiveSessionEventType.TOOL_CANCELLED, null);
            message.setCancellation(value(provider.get("toolCallCancellation")));
            messages.add(message);
        }

        JsonNode content = provider.path("serverContent");
        if(!content.isMissingNode()) {
            if(content.path("interrupted").asBoolean(false)) {
                messages.add(LiveSessionMessage.event(LiveSessionEventType.RESPONSE_INTERRUPTED, null));
            }
            addTranscription(messages, LiveSessionEventType.INPUT_TRANSCRIPT_DELTA, content.path("inputTranscription"));
            addTranscription(messages, LiveSessionEventType.OUTPUT_TRANSCRIPT_DELTA, content.path("outputTranscription"));
            JsonNode parts = content.path("modelTurn").path("parts");
            if(parts.isArray()) {
                parts.forEach(part -> addPart(messages, part));
            }
            if(content.path("turnComplete").asBoolean(false) || content.path("generationComplete").asBoolean(false)) {
                messages.add(LiveSessionMessage.event(LiveSessionEventType.RESPONSE_DONE, null));
            }
        }
        if(provider.has("usageMetadata")) {
            LiveSessionMessage message = LiveSessionMessage.event(LiveSessionEventType.USAGE_UPDATED, null);
            message.setUsage(usage(provider.get("usageMetadata")));
            messages.add(message);
        }
        if(provider.has("sessionResumptionUpdate")) {
            LiveSessionMessage message = LiveSessionMessage.event(LiveSessionEventType.SESSION_RESUMPTION, null);
            message.setResumption(value(provider.get("sessionResumptionUpdate")));
            messages.add(message);
        }
        if(provider.has("goAway")) {
            LiveSessionMessage message = LiveSessionMessage.event(LiveSessionEventType.SESSION_GOAWAY, null);
            message.setGoAway(value(provider.get("goAway")));
            messages.add(message);
        }
        return messages;
    }

    private void addTranscription(List<LiveSessionMessage> messages, LiveSessionEventType type, JsonNode transcription) {
        if(transcription != null && transcription.has("text")) {
            LiveSessionMessage message = LiveSessionMessage.event(type, null);
            message.setDelta(transcription.path("text").asText());
            messages.add(message);
        }
    }

    private void addPart(List<LiveSessionMessage> messages, JsonNode part) {
        if(part.has("inlineData")) {
            LiveSessionMessage message = LiveSessionMessage.event(LiveSessionEventType.OUTPUT_AUDIO_DELTA, null);
            message.setAudio(part.path("inlineData").path("data").asText());
            if(part.path("inlineData").has("mimeType")) {
                message.setPayload(Collections.singletonMap("mime_type", part.path("inlineData").path("mimeType").asText()));
            }
            messages.add(message);
        }
        if(part.has("text")) {
            LiveSessionMessage message = LiveSessionMessage.event(LiveSessionEventType.OUTPUT_TEXT_DELTA, null);
            message.setDelta(part.path("text").asText());
            messages.add(message);
        }
    }

    private LiveSessionUsage usage(JsonNode usage) {
        LiveSessionUsage converted = new LiveSessionUsage();
        applyModalityTokens(converted, usage.path("promptTokensDetails"), true);
        applyModalityTokens(converted, usage.path("responseTokensDetails"), false);
        if(converted.getInputTextTokens() == null) {
            converted.setInputTextTokens(longValue(usage, "promptTokenCount"));
        }
        if(converted.getOutputTextTokens() == null) {
            converted.setOutputTextTokens(longValue(usage, "candidatesTokenCount"));
        }
        converted.setCachedTokens(longValue(usage, "cachedContentTokenCount"));
        converted.setFinalUsageConfirmed(false);
        converted.getProvider().put("total_token_count", longValue(usage, "totalTokenCount"));
        return converted;
    }

    private void applyModalityTokens(LiveSessionUsage usage, JsonNode details, boolean input) {
        if(!details.isArray()) {
            return;
        }
        details.forEach(detail -> {
            long count = detail.path("tokenCount").asLong();
            String modality = detail.path("modality").asText("").toUpperCase(Locale.ROOT);
            if(input && "AUDIO".equals(modality)) {
                usage.setInputAudioTokens(sum(usage.getInputAudioTokens(), count));
            } else if(input && ("IMAGE".equals(modality) || "VIDEO".equals(modality))) {
                usage.setInputVideoTokens(sum(usage.getInputVideoTokens(), count));
            } else if(input && "TEXT".equals(modality)) {
                usage.setInputTextTokens(sum(usage.getInputTextTokens(), count));
            } else if(!input && "AUDIO".equals(modality)) {
                usage.setOutputAudioTokens(sum(usage.getOutputAudioTokens(), count));
            } else if(!input && "TEXT".equals(modality)) {
                usage.setOutputTextTokens(sum(usage.getOutputTextTokens(), count));
            }
        });
    }

    private long sum(Long current, long value) {
        return (current == null ? 0 : current) + value;
    }

    private void copy(Map<String, Object> source, ObjectNode target, String providerKey, String... aliases) {
        Object value = first(source, providerKey);
        if(value == null) {
            value = first(source, aliases);
        }
        if(value != null) {
            target.set(providerKey, JacksonUtils.MAPPER.valueToTree(value));
        }
    }

    private Object first(Map<String, Object> source, String... keys) {
        if(source == null) {
            return null;
        }
        for (String key : keys) {
            if(source.get(key) != null) {
                return source.get(key);
            }
        }
        return null;
    }

    private void put(ObjectNode target, String key, Object value) {
        if(value != null) {
            target.set(key, JacksonUtils.MAPPER.valueToTree(value));
        }
    }

    private Object value(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : JacksonUtils.MAPPER.convertValue(node, Object.class);
    }

    private Long longValue(JsonNode node, String field) {
        return node.has(field) ? node.path(field).asLong() : null;
    }

    private String stringValue(LiveSessionMessage message, String key, String defaultValue) {
        Object value = message.payloadValue(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }
}
