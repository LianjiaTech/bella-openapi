package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

public class AudioTranscriptionRequest {
    @Data
    public static class AudioTranscriptionReq {

        private String url;

        private String model;

        private String user = "";

        @JsonProperty("callback_url")
        private String callbackUrl;

        // ******************************公共******************************
        @JsonProperty("channel_number")
        private int channelNumber = 1;

        // 是否开启说话人分离
        @JsonProperty("speaker_diarization")
        private boolean speakerDiarization;

        // 说话人分离人数，配合speakerDiarization使用，0-即默认自动分离；取值范围[0-10
        @JsonProperty("speaker_number")
        private int speakerNumber;

        // 热词，协议处统一字段，各自 worker 适配
        // 比如讯飞是 hotWord，腾讯是 vocabId
        @JsonProperty("hot_word")
        private String hotWord;

        // ******************************讯飞******************************
        @JsonProperty("language")
        private String language = "cn";

        // 多候选开关
        // 0：关闭 (默认)
        // 1：打开
        @JsonProperty("candidate")
        private int candidate = 0;

        // 转写音频上传方式
        // fileStream：文件流 (默认)
        // urlLink：音频url外链
        @JsonProperty("audio_mode")
        private String audioMode = "urlLink";

        // 是否标准pcm/wav(16k/16bit/单声道)
        // 0：非标准 wav (默认)
        // 1：标准pcm/wav
        @JsonProperty("standard_wav")
        private int standardWav = 0;

        // 语言识别模式选择，支持的语言识别模式选择如下：language 为 cn 时：
        // 1：自动中英文模式 (默认)
        // 2:中文模式（可能包含少量英文）
        // 4:纯中文模式（不包含英文）
        @JsonProperty("language_type")
        private int languageType = 2;

        // 翻译模式（默认 2：按段落进行翻译，目前只支持按段落进行翻译），使用翻译能力时该字段生效
        // 1：按 VAD 进行翻译；
        // 2：按段落进行翻译；
        // 3：按整篇进行翻译；
        @JsonProperty("trans_mode")
        private int transMode = 2;

        // 顺滑开关
        // true：表示开启 (默认)
        // false：表示关闭
        @JsonProperty("eng_smoothproc")
        private boolean engSmoothproc = true;

        // 口语规整开关，口语规整是顺滑的升级版本
        // true：表示开启
        // false：表示关闭 (默认)
        // 1.当 eng_smoothproc 为 false，eng_colloqproc 为 false 时只返回原始转写结果
        // 2.当 eng_smoothproc 为 true，eng_colloqproc 为 false 时返回包含顺滑词的结果和原始结果
        // 3. 当 eng_smoothproc 为 true，eng_colloqproc 为 true 时返回包含口语规整的结果和原始结果
        // 4. 当 eng_smoothproc 为 false，eng_colloqproc 为 true 时返回包含口语规整的结果和原始结果
        @JsonProperty("eng_collogproc")
        private boolean engCollogproc = false;

        // 远近场模式
        // 1：远场模式 (默认)
        // 2：近场模式
        @JsonProperty("eng_vad_mdn")
        private int engVadMdn = 1;

        // 首尾是否带静音信息
        // 0：不显示
        // 1：显示 (默认)
        @JsonProperty("eng_vad_margin")
        private int engVadMargin = 1;

        // 针对粤语转写后的字体转换
        // 0：输出简体
        // 1：输出繁体 (默认)
        @JsonProperty("eng_rlang")
        private int engRlang = 1;

        // ******************************腾讯******************************
        // 热词表参数
        @JsonProperty("vocab_id")
        private String vocabId;

        // ******************************自研******************************
        // 采样率
        @JsonProperty("sample_rate")
        private int sampleRate;

        // 是否在返回结果中，添加分词信息
        @JsonProperty("enable_words")
        private boolean enableWords;

        // 是否启用静音检测
        @JsonProperty("enable_vad")
        private boolean enableVad = true;

        // 音频切片参数,默认10s
        @JsonProperty("chunk_length")
        private int chunkLength = 10;

        // 是否开启语义句子检测
        @JsonProperty("enable_semantic_sentence_detection")
        private boolean enableSemanticSentenceDetection;

        // 是否开启标点符号补充
        @JsonProperty("enable_punctuation_prediction")
        private boolean enablePunctuationPrediction;

        // 最大静音时长，单位毫秒
        @JsonProperty("max_end_silence")
        private int maxEndSilence;

        // ******************************火山******************************
        @JsonProperty("enable_itn")
        private boolean enableItn = true;

        @JsonProperty("enable_ddc")
        private boolean enableDdc = false;

        @JsonProperty("enable_channel_split")
        private boolean enableChannelSplit = false;

        @JsonProperty("show_utterances")
        private boolean showUtterances = true;

        @JsonProperty("vad_segment")
        private boolean vadSegment = false;

        @JsonProperty("sensitive_words_filter")
        private String sensitiveWordsFilter;

        @JsonProperty("boosting_table_name")
        private String boostingTableName;

        @JsonProperty("embedding_model")
        private String embeddingModel;

        @JsonProperty("context")
        private String context;
    }

    @Data
    public static class AudioTranscriptionResultReq {
        @NotEmpty
        @JsonProperty("task_id")
        private List<String> taskId;
    }
}
