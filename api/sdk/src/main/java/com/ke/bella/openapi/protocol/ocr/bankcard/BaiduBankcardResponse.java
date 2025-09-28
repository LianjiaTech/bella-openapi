package com.ke.bella.openapi.protocol.ocr.bankcard;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 百度银行卡OCR API原始响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class BaiduBankcardResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("log_id")
    private Long logId;                 // 请求标识码

    @JsonProperty("direction")
    private Integer direction;          // 图像方向：-1未定义，0正向，1逆时针90度，2逆时针180度，3逆时针270度

    @JsonProperty("result")
    private BankcardResult result;      // 返回结果

    // 错误信息
    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_msg")
    private String errorMsg;

    /**
     * 银行卡识别结果
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BankcardResult implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("bank_card_number")
        private String bankCardNumber;          // 银行卡卡号

        @JsonProperty("valid_date")
        private String validDate;               // 有效期

        @JsonProperty("bank_card_type")
        private Integer bankCardType;           // 银行卡类型：0不能识别，1借记卡，2贷记卡，3准贷记卡，4预付费卡

        @JsonProperty("bank_name")
        private String bankName;                // 银行名

        @JsonProperty("holder_name")
        private String holderName;              // 持卡人姓名

        @JsonProperty("bank_card_number_location")
        private Location bankCardNumberLocation; // 银行卡号位置坐标

        @JsonProperty("card_quality")
        private CardQuality cardQuality;        // 银行卡质量检测
    }

    /**
     * 位置信息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Location implements Serializable {
        private static final long serialVersionUID = 1L;

        private String left;           // 左上角水平坐标
        private String top;            // 左上角垂直坐标
        private String width;          // 宽度
        private String height;         // 高度
    }

    /**
     * 银行卡质量检测
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CardQuality implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("IsClear")
        private String isClear;                    // 是否清晰

        @JsonProperty("IsClear_probability")
        private String isClearProbability;         // 清晰度概率

        @JsonProperty("IsComplete")
        private String isComplete;                 // 是否边框/四角完整

        @JsonProperty("IsComplete_probability")
        private String isCompleteProbability;      // 完整性概率
    }
}
