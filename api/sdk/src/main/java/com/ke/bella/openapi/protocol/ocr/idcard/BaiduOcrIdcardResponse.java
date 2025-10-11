package com.ke.bella.openapi.protocol.ocr.idcard;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * 百度OCR API原始响应
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class BaiduOcrIdcardResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    // 基本响应字段
    @JsonProperty("log_id")
    private Long logId;                 // 唯一log id

    @JsonProperty("direction")
    private Integer direction;          // 图像方向

    @JsonProperty("image_status")
    private String imageStatus;         // 图像状态

    @JsonProperty("risk_type")
    private String riskType;           // 风险类型

    @JsonProperty("edit_tool")
    private String editTool;           // 编辑工具检测结果

    @JsonProperty("words_result")
    private Map<String, WordResult> wordsResult;    // 识别结果

    @JsonProperty("words_result_num")
    private Integer wordsResultNum;     // 识别结果数量

    @JsonProperty("idcard_type")
    private String idcardType;          // 身份证类型：normal(正常)或temporary(临时)

    // 错误信息
    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_msg")
    private String errorMsg;

    /**
     * 单个识别字段结果
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WordResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private Location location;      // 位置信息
        private String words;           // 识别内容
    }

    /**
     * 位置信息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Location implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer left;           // 左上角x坐标
        private Integer top;            // 左上角y坐标
        private Integer width;          // 宽度
        private Integer height;         // 高度
    }
}
