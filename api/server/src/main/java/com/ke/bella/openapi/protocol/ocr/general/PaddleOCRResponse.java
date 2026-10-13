package com.ke.bella.openapi.protocol.ocr.general;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduBaseResponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 百度智能云千帆 PaddleOCR-VL 响应结构
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaddleOCRResponse extends BaiduBaseResponse {
    private static final long serialVersionUID = 1L;

    /**
     * 请求唯一标识
     */
    private String id;

    /**
     * 识别结果
     */
    private Object result;

    /**
     * 错误信息（PaddleOCR-VL 特有格式）
     * 示例: {"code": "invalid_argument", "message": "fetch object failed", "type": "invalid_request_error"}
     */
    private Object error;
}
