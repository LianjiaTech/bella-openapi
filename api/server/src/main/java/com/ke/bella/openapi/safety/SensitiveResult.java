package com.ke.bella.openapi.safety;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 敏感数据检测结果项
 * 以json_path为维度的检测结果
 */
@Data
public class SensitiveResult {

    /**
     * 协议中检测敏感数据的JSON路径，RFC 9535格式
     * 例如：$.messages[1].content[0].text
     */
    @JsonProperty("json_path")
    private String json_path;

    /**
     * 默认脱敏规则脱敏后的文本，建议使用，仅供参考
     */
    @JsonProperty("content_masked")
    private String content_masked;

    /**
     * 此json路径下检测到的敏感数据数量
     */
    @JsonProperty("count")
    private Integer count;

    /**
     * 检测到的敏感数据列表
     */
    @JsonProperty("detected")
    private List<DetectedItem> detected;
}