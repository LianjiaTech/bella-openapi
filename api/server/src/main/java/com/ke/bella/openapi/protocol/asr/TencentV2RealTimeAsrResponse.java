package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TencentV2RealTimeAsrResponse {
    private Integer code;
    private String message;
    private String voice_id;
    private String message_id;
    @JsonProperty("speaker_context_id")
    private String speakerContextId;
    private Sentences sentences;

    @JsonProperty("final")
    private Integer finalFlag;

    @Data
    @NoArgsConstructor
    public static class Sentences {
        private List<Sentence> sentence_list;
    }

    @Data
    @NoArgsConstructor
    public static class Sentence {
        private String sentence;
        private Integer sentence_type;
        private Integer sentence_id;
        private Integer speaker_id;
        private Integer start_time;
        private Integer end_time;

        public boolean isDefinite() {
            return sentence_type != null && sentence_type == 1;
        }

        public boolean isIntermediate() {
            return sentence_type != null && sentence_type == 0;
        }
    }

    public boolean isSuccess() {
        return code != null && code == 0;
    }

    public boolean isFinal() {
        return finalFlag != null && finalFlag == 1;
    }
}
