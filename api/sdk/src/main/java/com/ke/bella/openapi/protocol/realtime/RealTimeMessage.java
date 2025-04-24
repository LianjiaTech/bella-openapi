package com.ke.bella.openapi.protocol.realtime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 实时语音识别消息
 * 用于所有ASR服务的请求和响应
 */
@Data
public class RealTimeMessage extends OpenapiResponse {
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    private String apikey;

    /**
     * 消息头
     * name字段决定消息类型
     */
    private RealTimeHeader header;
    
    /**
     * 消息负载
     * 根据消息类型不同而有不同的内容
     */
    private Payload payload;

    public static RealTimeMessage startedResponse(String taskId) {
        RealTimeMessage response = new RealTimeMessage();
        response.setHeader(RealTimeHeader.header(RealTimeEventType.TRANSCRIPTION_STARTED, taskId));
        return response;
    }

    public static RealTimeMessage errorResponse(int httpCode, int status,  String errorMessage, String taskId) {
        RealTimeMessage response = new RealTimeMessage();
        OpenapiError error = OpenapiError.builder().httpCode(httpCode).code(String.valueOf(status)).message(errorMessage).build();
        response.setError(error);
        response.setHeader(RealTimeHeader.errorHeader(status, errorMessage, taskId));
        return response;
    }

    public static RealTimeMessage sentenceBegin(String taskId, Payload payload) {
        RealTimeMessage response = new RealTimeMessage();
        response.setHeader(RealTimeHeader.header(RealTimeEventType.SENTENCE_BEGIN, taskId));
        response.setPayload(payload);
        return response;
    }

    public static RealTimeMessage resultChange(String taskId, Payload payload) {
        RealTimeMessage response = new RealTimeMessage();
        response.setHeader(RealTimeHeader.header(RealTimeEventType.TRANSCRIPTION_RESULT_CHANGED, taskId));
        response.setPayload(payload);
        return response;
    }

    public static RealTimeMessage sentenceEnd(String taskId, Payload payload) {
        RealTimeMessage response = new RealTimeMessage();
        response.setHeader(RealTimeHeader.header(RealTimeEventType.SENTENCE_END, taskId));
        response.setPayload(payload);
        return response;
    }

    public static RealTimeMessage completion(String taskId) {
        RealTimeMessage response = new RealTimeMessage();
        response.setHeader(RealTimeHeader.header(RealTimeEventType.TRANSCRIPTION_COMPLETED, taskId));
        return response;
    }
    
    /**
     * 消息负载
     * 包含识别结果或音频格式参数
     */
    @Data
    public static class Payload implements Serializable {
        private static final long serialVersionUID = 1L;
        
        // === 响应消息中使用的字段 ===
        
        /**
         * 句子编号，从0或1开始递增
         * 在SentenceBegin, TranscriptionResultChanged, SentenceEnd消息中使用
         */
        private Integer index;
        
        /**
         * 当前已处理的音频时长，单位是毫秒
         * 在SentenceBegin, TranscriptionResultChanged, SentenceEnd消息中使用
         */
        private Integer time;
        
        /**
         * 当前句子对应的SentenceBegin事件的时间，单位是毫秒
         * 在SentenceEnd消息中使用
         */
        @JsonProperty("begin_time")
        private Integer beginTime;

        /**
         * 当前句子的起始帧
         */
        @JsonProperty("start_frame")
        private Integer startFrame;

        /**
         * 当前句子的结束帧
         */
        @JsonProperty("end_frame")
        private Integer endFrame;
        
        /**
         * 当前的识别结果
         * 在TranscriptionResultChanged, SentenceEnd消息中使用
         */
        private String result;

        /**
         * LLM处理结果
         */
        private Object data;

        /**
         * 当前时间
         */
        private Double timestamp;

        /**
         * 从当前frame到起始frame的时间间隔
         */
        private Integer duration;

        /**
         * TTS_TTFT中从检测到结束说话到生成第一句话的时间间隔
         */
        private Integer latency;

        /**
         * 当前句子的词信息列表
         * 在TranscriptionResultChanged, SentenceEnd消息中使用
         */
        private List<Word> words;
        
        /**
         * 当前句子识别结果的置信度，取值范围：[0.0,1.0]
         * 在TranscriptionResultChanged, SentenceEnd消息中使用
         */
        private Double confidence;
        
        /**
         * 当前句子的情感
         * 包含positive(正面情感)、negative(负面情感)、neutral(无明显情感)三种类别
         * 在SentenceEnd消息中使用
         */
        @JsonProperty("emo_tag")
        private String emoTag;
        
        /**
         * 当前句子识别情感的置信度，取值范围：[0.0,1.0]
         * 在SentenceEnd消息中使用
         */
        @JsonProperty("emo_confidence")
        private Double emoConfidence;
        
        /**
         * 会话ID
         * 在TranscriptionStarted消息中使用
         */
        @JsonProperty("session_id")
        private String sessionId;
        
        // === 在StartTranscription请求中使用的字段 ===
        
        /**
         * 音频格式
         */
        private String format;
        
        /**
         * 采样率
         */
        @JsonProperty("sample_rate")
        private Integer sampleRate;
        
        /**
         * 是否启用中间结果
         */
        @JsonProperty("enable_intermediate_result")
        private Boolean enableIntermediateResult;
        
        /**
         * 是否启用标点预测
         */
        @JsonProperty("enable_punctuation_prediction")
        private Boolean enablePunctuationPrediction;
        
        /**
         * 是否启用逆文本规范化
         */
        @JsonProperty("enable_inverse_text_normalization")
        private Boolean enableInverseTextNormalization;

        /**
         * 自定义模型ID
         */
        @JsonProperty("customization_id")
        private String customizationId;
        
        /**
         * 词汇表ID
         */
        @JsonProperty("vocabulary_id")
        private String vocabularyId;
        
        /**
         * 最大句子静音时长
         */
        @JsonProperty("max_sentence_silence")
        private String maxSentenceSilence;
        
        /**
         * 是否启用词级别时间戳
         */
        @JsonProperty("enable_words")
        private Boolean enableWords;
        
        /**
         * 是否忽略句子超时
         */
        @JsonProperty("enable_ignore_sentence_timeout")
        private Boolean enableIgnoreSentenceTimeout;
        
        /**
         * 是否处理口吃
         */
        private Boolean disfluency;
        
        /**
         * 语音噪声阈值
         */
        @JsonProperty("speech_noise_threshold")
        private Float speechNoiseThreshold;
        
        /**
         * 是否启用语义句子检测
         */
        @JsonProperty("enable_semantic_sentence_detection")
        private Boolean enableSemanticSentenceDetection;
        
        /**
         * LLM选项配置
         */
        @JsonProperty("llm_option")
        private LlmOption llmOption;
        
        /**
         * TTS选项配置
         */
        @JsonProperty("tts_option")
        private TtsOption ttsOption;
        
        /**
         * 变量配置
         */
        private Map<String, Object> variables;
    }
    
    /**
     * LLM选项配置类
     * 用于配置大语言模型的参数
     */
    @Data
    public static class LlmOption implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 主要LLM配置
         */
        private MainLlmOption main;
        
        /**
         * 工作者LLM配置列表
         */
        private List<WorkerLlmOption> workers;
    }
    
    /**
     * 主要LLM配置
     */
    @Data
    public static class MainLlmOption implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 模型名称
         */
        private String model;
        
        /**
         * 系统提示词
         * 用于设置模型的行为和角色
         */
        @JsonProperty("sys_prompt")
        private String sysPrompt;
        
        /**
         * 用户提示词
         * 用于引导模型生成特定内容
         */
        private String prompt;
        
        /**
         * 温度参数
         * 控制输出的随机性，值越大随机性越高，取值范围[0.0, 2.0]
         */
        private Float temperature = 1.0f;
    }
    
    /**
     * 工作者LLM配置
     */
    @Data
    public static class WorkerLlmOption implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 模型名称
         */
        private String model;
        
        /**
         * 是否阻塞
         */
        private Boolean blocking = false;
        
        /**
         * 结果变量名
         */
        @JsonProperty("variable_name")
        private String variableName;
        
        /**
         * 变量类型，如text、json等
         */
        @JsonProperty("variable_type")
        private String variableType;
        
        /**
         * 系统提示词
         */
        @JsonProperty("sys_prompt")
        private String sysPrompt;
        
        /**
         * 用户提示词
         */
        private String prompt;
        
        /**
         * 温度参数
         */
        private Float temperature = 1.0f;
        
        /**
         * JSON模式定义
         */
        @JsonProperty("json_schema")
        private String jsonSchema;
    }
    
    /**
     * TTS选项配置类
     * 用于配置文本转语音的参数
     */
    @Data
    public static class TtsOption implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 模型名称
         */
        private String model;
        
        /**
         * 语音角色
         */
        private String voice;
        
        /**
         * 采样率
         */
        @JsonProperty("sample_rate")
        private Integer sampleRate;
    }
    
    /**
     * 词信息
     */
    @Data
    public static class Word implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 文本
         */
        private String text;
        
        /**
         * 词开始时间，单位为毫秒
         */
        @JsonProperty("start_time")
        private Integer startTime;
        
        /**
         * 词结束时间，单位为毫秒
         */
        @JsonProperty("end_time")
        private Integer endTime;
        
        /**
         * 词的置信度
         */
        private Double probability;
    }
}
