package com.ke.bella.openapi.protocol.asr;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;

import java.util.List;

/**
 * 腾讯实时语音识别响应
 */
@Data
@NoArgsConstructor
public class TencentRealTimeAsrResponse {
    private Integer code; // 状态码，0代表正常
    private String message; // 错误说明
    private String voice_id; // 音频流全局唯一标识
    private String message_id; // 本message唯一ID
    private Result result; // 识别结果
    private Integer final_; // 该字段返回1时表示音频流全部识别结束

    @Data
    @NoArgsConstructor
    public static class Result {
        private Integer slice_type; // 识别结果类型：0-开始，1-识别中，2-识别结束
        private Integer index; // 当前一段话结果序号
        private Integer start_time; // 当前一段话起始时间（毫秒）
        private Integer end_time; // 当前一段话结束时间（毫秒）
        private String voice_text_str; // 当前一段话文本结果
        private Integer word_size; // 当前一段话的词结果个数
        private List<Word> word_list; // 当前一段话的词列表

        @Data
        @NoArgsConstructor
        public static class Word {
            private String word; // 词内容
            private Integer start_time; // 词的起始时间（毫秒）
            private Integer end_time; // 词的结束时间（毫秒）
            private Integer stable_flag; // 词的稳态结果，0-可能变化，1-不会变化

            public RealTimeMessage.Word convert() {
                RealTimeMessage.Word rtWord = new RealTimeMessage.Word();
                rtWord.setText(this.word);
                rtWord.setStartTime(this.start_time);
                rtWord.setEndTime(this.end_time);
                rtWord.setProbability(this.stable_flag == 1 ? 1.0 : 0.5);
                return rtWord;
            }
        }

        /**
         * 判断是否是稳定结果（slice_type=2表示识别结束）
         */
        public boolean isDefinite() {
            return slice_type != null && slice_type == 2;
        }

        /**
         * 判断是否是开始信号（slice_type=0表示一段话开始）
         */
        public boolean isBegin() {
            return slice_type != null && slice_type == 0;
        }

        /**
         * 判断是否是中间结果（slice_type=1表示识别中）
         */
        public boolean isIntermediate() {
            return slice_type != null && slice_type == 1;
        }

        /**
         * 获取持续时间
         */
        public int getDuration() {
            if(start_time != null && end_time != null) {
                return end_time - start_time;
            }
            return 0;
        }
    }

    /**
     * 判断是否识别成功
     */
    public boolean isSuccess() {
        return code != null && code == 0;
    }

    /**
     * 判断是否是最终消息
     */
    public boolean isFinal() {
        return final_ != null && final_ == 1;
    }
}
