package com.ke.bella.openapi.protocol.asr.flash;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HuoshanBigModelFlashAsrRequest {
    private User user;
    private Audio audio;
    private RequestOptions request;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class User {
        private String uid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Audio {
        private String format;
        private String codec;
        private Integer rate;
        private Integer bits;
        private Integer channel;
        private String data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RequestOptions {
        @JsonProperty("model_name")
        private String modelName;
        @JsonProperty("enable_punc")
        private Boolean enablePunc;
        @JsonProperty("enable_itn")
        private Boolean enableItn;
        @JsonProperty("enable_speaker_info")
        private Boolean enableSpeakerInfo;
        @JsonProperty("enable_ddc")
        private Boolean enableDdc;
        @JsonProperty("output_zh_variant")
        private String outputZhVariant;
        @JsonProperty("enable_auto_lang")
        private Boolean enableAutoLang;
        @JsonProperty("show_utterances")
        private Boolean showUtterances;
        @JsonProperty("result_type")
        private String resultType;
        @JsonProperty("enable_accelerate_text")
        private Boolean enableAccelerateText;
        @JsonProperty("accelerate_score")
        private Double accelerateScore;
        @JsonProperty("vad_segment_duration")
        private Integer vadSegmentDuration;
        @JsonProperty("end_window_size")
        private Integer endWindowSize;
        @JsonProperty("force_to_speech_time")
        private Integer forceToSpeechTime;
        @JsonProperty("sensitive_words_filter")
        private String sensitiveWordsFilter;
        @JsonProperty("enable_poi_fc")
        private Boolean enablePoiFc;
        @JsonProperty("enable_music_fc")
        private Boolean enableMusicFc;
        @JsonProperty("boosting_table_name")
        private String boostingTableName;
        @JsonProperty("correct_table_name")
        private String correctTableName;
        @JsonProperty("correct_table_id")
        private String correctTableId;
        @JsonProperty("hot_words")
        private String hotWords;
        @JsonProperty("hot_words_table_id")
        private String hotWordsTableId;
        private String language;
        private String context;
    }
}
