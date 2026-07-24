package com.ke.bella.openapi.protocol.tts;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AliCosyVoiceRequest implements IMemoryClearable, ITransfer {
    private static final String INPUT = "input";
    private static final String EXTRA_BODY = "extra_body";
    private static final String TEXT = "text";
    private static final String VOICE = "voice";
    private static final String FORMAT = "format";
    private static final String SAMPLE_RATE = "sample_rate";
    private static final String RATE = "rate";
    private static final Set<String> ROOT_RESERVED_FIELDS = new HashSet<>(Arrays.asList("model", INPUT, EXTRA_BODY));
    private static final Set<String> INPUT_RESERVED_FIELDS = new HashSet<>(Arrays.asList(TEXT, VOICE, FORMAT, SAMPLE_RATE, RATE));

    private String model;
    private Input input;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        if(additionalProperties == null) {
            additionalProperties = new HashMap<>();
        }
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String key, Object value) {
        getAdditionalProperties().put(key, value);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Input {
        private String text;
        private String voice;
        private String format;
        @JsonProperty(SAMPLE_RATE)
        private Integer sampleRate;
        private Double rate;

        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            if(additionalProperties == null) {
                additionalProperties = new HashMap<>();
            }
            return additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String key, Object value) {
            getAdditionalProperties().put(key, value);
        }
    }

    public static AliCosyVoiceRequest from(TtsRequest ttsRequest, AliCosyVoiceProperty property) {
        Map<String, Object> extraBody = normalizeExtraBody(ttsRequest.getExtra_body());
        Input input = new Input();

        if(extraBody != null && extraBody.get(INPUT) instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inputMap = (Map<String, Object>) extraBody.get(INPUT);
            for(Map.Entry<String, Object> entry : inputMap.entrySet()) {
                if(!INPUT_RESERVED_FIELDS.contains(entry.getKey())) {
                    input.setAdditionalProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        input.setText(ttsRequest.getInput());
        input.setVoice(StringUtils.isNotBlank(ttsRequest.getVoice()) ? ttsRequest.getVoice() : property.getDefaultVoice());
        input.setFormat(StringUtils.isNotBlank(ttsRequest.getResponseFormat())
                ? ttsRequest.getResponseFormat()
                : property.getDefaultContentType());
        input.setSampleRate(ttsRequest.getSampleRate() != null ? ttsRequest.getSampleRate() : property.getDefaultSampleRate());
        input.setRate(ttsRequest.getSpeed());

        AliCosyVoiceRequest request = AliCosyVoiceRequest.builder()
                .model(StringUtils.isNotBlank(property.getDeployName()) ? property.getDeployName() : ttsRequest.getModel())
                .input(input)
                .build();

        if(extraBody != null) {
            for(Map.Entry<String, Object> entry : extraBody.entrySet()) {
                if(!ROOT_RESERVED_FIELDS.contains(entry.getKey())) {
                    request.setAdditionalProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        return request;
    }

    private static Map<String, Object> normalizeExtraBody(Map<String, Object> extraBody) {
        if(extraBody == null) {
            return null;
        }
        Map<String, Object> normalized = new HashMap<>();
        Object wrappedExtraBody = extraBody.get(EXTRA_BODY);
        if(wrappedExtraBody instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> wrappedMap = (Map<String, Object>) wrappedExtraBody;
            normalized.putAll(wrappedMap);
        }
        for(Map.Entry<String, Object> entry : extraBody.entrySet()) {
            if(!EXTRA_BODY.equals(entry.getKey())) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        return normalized;
    }

    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            if(input != null) {
                input.text = null;
            }
            cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
