package com.ke.bella.openapi.safety;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 单个敏感数据检测项
 */
@Data
public class DetectedItem {

    /**
     * 敏感数据起始位置
     */
    @JsonProperty("start")
    private Integer start;

    /**
     * 敏感数据结束位置
     */
    @JsonProperty("end")
    private Integer end;

    /**
     * 检测到的敏感数据原文
     */
    @JsonProperty("text")
    private String text;

    /**
     * 敏感数据类型，如 "PHONE", "ID_CARD", "EMAIL" 等
     */
    @JsonProperty("type")
    private String type;
}