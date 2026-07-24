package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.ISummary;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AsrRequest implements ISummary, IMemoryClearable {
    @JsonIgnore
    byte[] content;
    String model;
    String format;
    Integer maxSentenceSilence;
    Integer sampleRate;
    String hotWords;
    String hotWordsTableId;
    Boolean convertNumbers;

    String uid;
    String did;
    String platform;
    @JsonProperty("sdk_version")
    String sdkVersion;
    @JsonProperty("app_version")
    String appVersion;

    String language;
    String codec;
    Integer bits;
    Integer channel;

    @JsonProperty("enable_nonstream")
    Boolean enableNonstream;
    @JsonProperty("enable_itn")
    Boolean enableItn;
    @JsonProperty("enable_punc")
    Boolean enablePunc;
    @JsonProperty("enable_speaker_info")
    Boolean enableSpeakerInfo;
    @JsonProperty("ssd_version")
    String ssdVersion;
    @JsonProperty("enable_ddc")
    Boolean enableDdc;
    @JsonProperty("output_zh_variant")
    String outputZhVariant;
    @JsonProperty("enable_auto_lang")
    Boolean enableAutoLang;
    @JsonProperty("show_utterances")
    Boolean showUtterances;
    @JsonProperty("show_speech_rate")
    Boolean showSpeechRate;
    @JsonProperty("show_volume")
    Boolean showVolume;
    @JsonProperty("enable_lid")
    Boolean enableLid;
    @JsonProperty("enable_emotion_detection")
    Boolean enableEmotionDetection;
    @JsonProperty("enable_gender_detection")
    Boolean enableGenderDetection;
    @JsonProperty("result_type")
    String resultType;
    @JsonProperty("enable_accelerate_text")
    Boolean enableAccelerateText;
    @JsonProperty("accelerate_score")
    Double accelerateScore;
    @JsonProperty("vad_segment_duration")
    Integer vadSegmentDuration;
    @JsonProperty("end_window_size")
    Integer endWindowSize;
    @JsonProperty("force_to_speech_time")
    Integer forceToSpeechTime;
    @JsonProperty("sensitive_words_filter")
    String sensitiveWordsFilter;
    @JsonProperty("enable_poi_fc")
    Boolean enablePoiFc;
    @JsonProperty("enable_music_fc")
    Boolean enableMusicFc;

    @JsonProperty("boosting_table_name")
    String boostingTableName;
    @JsonProperty("correct_table_name")
    String correctTableName;
    @JsonProperty("correct_table_id")
    String correctTableId;
    String context;

    @Override
    public String[] ignoreFields() {
        return new String[] { "content" };
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            // 清理最大的内存占用 - 音频内容字节数组
            this.content = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
