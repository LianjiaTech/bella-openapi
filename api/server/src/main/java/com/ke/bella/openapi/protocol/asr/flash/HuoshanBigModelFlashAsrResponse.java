package com.ke.bella.openapi.protocol.asr.flash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HuoshanBigModelFlashAsrResponse {
    @JsonProperty("audio_info")
    private AudioInfo audioInfo;
    private Result result;
    private String message;
    private String logid;

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
        private String text;
        private List<Utterance> utterances;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Utterance {
        private String text;
        @JsonProperty("start_time")
        private Long startTime;
        @JsonProperty("begin_time")
        private Long beginTime;
        @JsonProperty("end_time")
        private Long endTime;

        public long effectiveBeginTime() {
            if(startTime != null) {
                return startTime;
            }
            return beginTime == null ? 0L : beginTime;
        }

        public long effectiveEndTime() {
            return endTime == null ? 0L : endTime;
        }
    }

    public int duration() {
        return audioInfo == null ? 0 : audioInfo.getDuration();
    }
}
