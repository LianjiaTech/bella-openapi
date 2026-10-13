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
public class MiniMaxRequest implements IMemoryClearable, ITransfer {
    private static final String VOICE_SETTING = "voice_setting";
    private static final String AUDIO_SETTING = "audio_setting";
    private static final String OUTPUT_FORMAT = "output_format";
    private static final String STREAM_OPTIONS = "stream_options";
    private static final String VOICE_ID = "voice_id";
    private static final String SPEED = "speed";
    private static final String FORMAT = "format";
    private static final String SAMPLE_RATE = "sample_rate";
    private static final Set<String> ROOT_RESERVED_FIELDS = new HashSet<>(Arrays.asList(
            "model", "text", "stream", STREAM_OPTIONS, OUTPUT_FORMAT, VOICE_SETTING, AUDIO_SETTING));
    private static final Set<String> VOICE_RESERVED_FIELDS = new HashSet<>(Arrays.asList(VOICE_ID, SPEED));
    private static final Set<String> AUDIO_RESERVED_FIELDS = new HashSet<>(Arrays.asList(FORMAT, SAMPLE_RATE));

    private String model;
    private String text;
    private Boolean stream;
    @JsonProperty(STREAM_OPTIONS)
    private StreamOptions streamOptions;
    @JsonProperty(OUTPUT_FORMAT)
    private String outputFormat;
    @JsonProperty(VOICE_SETTING)
    private VoiceSetting voiceSetting;
    @JsonProperty(AUDIO_SETTING)
    private AudioSetting audioSetting;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        if (additionalProperties == null) {
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
    public static class VoiceSetting {
        @JsonProperty(VOICE_ID)
        private String voiceId;
        private Double speed;

        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            if (additionalProperties == null) {
                additionalProperties = new HashMap<>();
            }
            return additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String key, Object value) {
            getAdditionalProperties().put(key, value);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AudioSetting {
        private String format;
        @JsonProperty(SAMPLE_RATE)
        private Integer sampleRate;

        @JsonIgnore
        private Map<String, Object> additionalProperties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            if (additionalProperties == null) {
                additionalProperties = new HashMap<>();
            }
            return additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String key, Object value) {
            getAdditionalProperties().put(key, value);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreamOptions {
        @JsonProperty("exclude_aggregated_audio")
        private Boolean excludeAggregatedAudio;
    }

    public static MiniMaxRequest from(TtsRequest ttsRequest, MiniMaxProperty property, boolean stream) {
        Map<String, Object> extraBody = ttsRequest.getExtra_body();
        VoiceSetting voiceSetting = new VoiceSetting();
        AudioSetting audioSetting = new AudioSetting();

        if (extraBody != null) {
            applyVoiceSetting(voiceSetting, extraBody.get(VOICE_SETTING));
            applyAudioSetting(audioSetting, extraBody.get(AUDIO_SETTING));
        }

        String model = StringUtils.isNotBlank(property.getDeployName()) ? property.getDeployName() : ttsRequest.getModel();
        String voice = StringUtils.isNotBlank(ttsRequest.getVoice()) ? ttsRequest.getVoice() : property.getDefaultVoice();
        if (StringUtils.isNotBlank(voice)) {
            voiceSetting.setVoiceId(voice);
        }
        if (ttsRequest.getSpeed() != null) {
            voiceSetting.setSpeed(ttsRequest.getSpeed());
        }
        if (StringUtils.isNotBlank(ttsRequest.getResponseFormat())) {
            audioSetting.setFormat(ttsRequest.getResponseFormat());
        } else if (StringUtils.isNotBlank(property.getDefaultContentType())) {
            audioSetting.setFormat(property.getDefaultContentType());
        }
        if (ttsRequest.getSampleRate() != null) {
            audioSetting.setSampleRate(ttsRequest.getSampleRate());
        } else if (property.getDefaultSampleRate() != null) {
            audioSetting.setSampleRate(property.getDefaultSampleRate());
        }

        MiniMaxRequest request = MiniMaxRequest.builder()
                .model(model)
                .text(ttsRequest.getInput())
                .stream(stream)
                .streamOptions(stream
                        ? StreamOptions.builder().excludeAggregatedAudio(true).build()
                        : null)
                .outputFormat("hex")
                .voiceSetting(voiceSetting)
                .audioSetting(audioSetting)
                .build();

        if (extraBody != null) {
            for (Map.Entry<String, Object> entry : extraBody.entrySet()) {
                if (!ROOT_RESERVED_FIELDS.contains(entry.getKey())) {
                    request.setAdditionalProperty(entry.getKey(), entry.getValue());
                }
            }
        }

        return request;
    }

    private static void applyVoiceSetting(VoiceSetting voiceSetting, Object voiceSettingObj) {
        if (!(voiceSettingObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> voiceMap = (Map<String, Object>) voiceSettingObj;
        for (Map.Entry<String, Object> entry : voiceMap.entrySet()) {
            if (VOICE_ID.equals(entry.getKey()) && entry.getValue() instanceof String) {
                voiceSetting.setVoiceId((String) entry.getValue());
            } else if (SPEED.equals(entry.getKey()) && entry.getValue() instanceof Number) {
                voiceSetting.setSpeed(((Number) entry.getValue()).doubleValue());
            } else if (!VOICE_RESERVED_FIELDS.contains(entry.getKey())) {
                voiceSetting.setAdditionalProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void applyAudioSetting(AudioSetting audioSetting, Object audioSettingObj) {
        if (!(audioSettingObj instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> audioMap = (Map<String, Object>) audioSettingObj;
        for (Map.Entry<String, Object> entry : audioMap.entrySet()) {
            if (FORMAT.equals(entry.getKey()) && entry.getValue() instanceof String) {
                audioSetting.setFormat((String) entry.getValue());
            } else if (SAMPLE_RATE.equals(entry.getKey()) && entry.getValue() instanceof Number) {
                audioSetting.setSampleRate(((Number) entry.getValue()).intValue());
            } else if (!AUDIO_RESERVED_FIELDS.contains(entry.getKey())) {
                audioSetting.setAdditionalProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if (!cleared) {
            this.text = null;
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
