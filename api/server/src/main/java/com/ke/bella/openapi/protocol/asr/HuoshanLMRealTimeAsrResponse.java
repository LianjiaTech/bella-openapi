package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 火山引擎大模型流式语音识别响应类
 * 根据文档 https://www.volcengine.com/docs/6561/1354869
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HuoshanLMRealTimeAsrResponse {

    private AudioInfo audio_info;
    private int code;
    private String message;
    private int sequence;
    private Result result;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AudioInfo {
        private int duration;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        // 整个音频的识别结果文本
        private String text;
        // 识别结果语音分句信息
        private List<Utterance> utterances;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Utterance {
        // utterance级的文本内容
        private String text;
        // 起始时间（毫秒）
        private int start_time;
        // 结束时间（毫秒）
        private int end_time;
        // 是否是一个确定分句
        private boolean definite;
        // 置信度
        private double confidence;
        // 词级别信息
        private List<Word> words;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Word {
        private String text;
        private int start_time;
        private int end_time;
        private int blank_duration;
        private double confidence;

        public RealTimeMessage.Word convert() {
            RealTimeMessage.Word word = new RealTimeMessage.Word();
            word.setStartTime(this.start_time);
            word.setEndTime(this.end_time);
            word.setProbability(this.confidence);
            word.setText(this.text);
            return word;
        }
    }

    /**
     * 获取音频时长
     */
    public int getDuration() {
        return audio_info != null ? audio_info.getDuration() : 0;
    }
}
