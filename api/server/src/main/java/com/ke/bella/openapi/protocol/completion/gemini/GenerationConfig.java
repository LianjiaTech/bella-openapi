package com.ke.bella.openapi.protocol.completion.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerationConfig {
    private Float temperature;
    private Float topP;
    private Integer topK;
    private Integer candidateCount;
    private Integer maxOutputTokens;
    private List<String> stopSequences;
    private Float presencePenalty;
    private Float frequencyPenalty;
    private String responseMimeType;
    private Map<String, Object> responseSchema;
    private Integer seed;
    private Boolean responseLogprobs;
    private Integer logprobs;
    private Boolean audioTimestamp;
    private ThinkingConfig thinkingConfig;
    private Object responseJsonSchema;
    private String mediaResolution;
    private SpeechConfig speechConfig;
    private Boolean enableAffectiveDialog;
    private ImageConfig imageConfig;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ThinkingConfig {
        private Integer thinkingBudget = -1;
        private boolean includeThoughts = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SpeechConfig {
        private VoiceConfig voiceConfig;
        private String languageCode;
        private MultiSpeakerVoiceConfig multiSpeakerVoiceConfig;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VoiceConfig {
        private PrebuiltVoiceConfig prebuiltVoiceConfig;
        private ReplicatedVoiceConfig replicatedVoiceConfig;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PrebuiltVoiceConfig {
        private String voiceName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReplicatedVoiceConfig {
        private String mimeType;
        private String voiceSampleAudio; // base64-encoded
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MultiSpeakerVoiceConfig {
        private List<SpeakerVoiceConfig> speakerVoiceConfigs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SpeakerVoiceConfig {
        private String speaker;
        private VoiceConfig voiceConfig;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageConfig {
        private ImageOutputOptions imageOutputOptions;
        private String aspectRatio;
        private String personGeneration;
        private String imageSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageOutputOptions {
        private String mimeType;
        private Integer compressionQuality;
    }
}
