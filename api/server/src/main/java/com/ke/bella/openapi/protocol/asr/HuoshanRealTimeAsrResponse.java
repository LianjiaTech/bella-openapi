package com.ke.bella.openapi.protocol.asr;

import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@Data
public class HuoshanRealTimeAsrResponse {

    private String reqid;
    private Addition addition;
    private int code;
    private String message;
    private int sequence;
    private java.util.List<Result> result;

    @Data
    @NoArgsConstructor
    public static class Addition {
        private int duration;
        private String logid;
    }

    @Data
    @NoArgsConstructor
    public static class Result {
        private int begin_time;
        private int end_time;
        // 默认每次返回所有分句结果。
        // 如果想每次只返回当前分句结果，则设置 show_utterances=true 和 result_type=single；
        // 如果当前分句结果是中间结果则返回的 definite=false，如果是分句最终结果则返回的 definite=true
        private boolean definite;
        private String text;
        private List<Result> utterances;
        private double confidence;
        private java.util.List<Word> words;

        @Data
        @NoArgsConstructor
        public static class Word {
            private String text;
            private double confidence;
            private int begin_time;
            private int end_time;

            public RealTimeMessage.Word convert() {
                RealTimeMessage.Word word = new RealTimeMessage.Word();
                word.setStartTime(this.begin_time);
                word.setEndTime(this.end_time);
                word.setProbability(confidence);
                word.setText(text);
                return word;
            }
        }

        public int getBeginTime() {
            return CollectionUtils.isEmpty(words) ? 0 : words.get(0).begin_time;
        }

        public int getTime() {
            return CollectionUtils.isEmpty(words) ? 0 : (words.get(words.size() - 1).end_time - words.get(0).begin_time);
        }
    }
}
