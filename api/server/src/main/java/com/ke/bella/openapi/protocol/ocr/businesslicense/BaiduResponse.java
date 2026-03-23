package com.ke.bella.openapi.protocol.ocr.businesslicense;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduBaseResponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 百度营业执照识别响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode(callSuper = true)
public class BaiduResponse extends BaiduBaseResponse {
    private static final long serialVersionUID = 1L;

    /**
     * 图像方向
     * -1：未定义，0：正向，1：逆时针90度，2：逆时针180度，3：逆时针270度
     */
    @JsonProperty("direction")
    private Integer direction;

    /**
     * 风险类型（当 risk_warn=true 时返回）
     * normal-正常营业执照；copy-复印件；screen-翻拍；scan-扫描
     */
    @JsonProperty("risk_type")
    private String riskType;

    /**
     * 识别结果数量
     */
    @JsonProperty("words_result_num")
    private Integer wordsResultNum;

    /**
     * 识别结果，key为字段名，value为识别内容和位置
     */
    @JsonProperty("words_result")
    private Map<String, WordResult> wordsResult;

    /**
     * 质量检测结果（当 detect_quality=true 时返回）
     */
    @JsonProperty("card_quality")
    private CardQuality cardQuality;

    /**
     * 单个识别字段结果
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WordResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 位置信息
         */
        private Location location;

        /**
         * 识别内容
         */
        private String words;
    }

    /**
     * 位置信息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Location implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 左上角x坐标
         */
        private Integer left;

        /**
         * 左上角y坐标
         */
        private Integer top;

        /**
         * 宽度
         */
        private Integer width;

        /**
         * 高度
         */
        private Integer height;
    }

    /**
     * 质量检测结果
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CardQuality implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 是否清晰，1：清晰，0：不清晰
         */
        @JsonProperty("is_clear")
        private String isClear;

        /**
         * "是否清晰"质量类型对应的概率，值在0-1之间
         */
        @JsonProperty("is_clear_propobility")
        private String isClearPropobility;

        /**
         * 是否边框/四角完整，1：完整，0：不完整
         */
        @JsonProperty("is_complete")
        private String isComplete;

        /**
         * "是否边框/四角完整"质量类型对应的概率，值在0-1之间
         */
        @JsonProperty("is_complete_propobility")
        private String isCompletePropobility;

        /**
         * 位置信息
         */
        private Location location;
    }
}
