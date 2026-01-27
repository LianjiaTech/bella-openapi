package com.ke.bella.openapi.protocol.ocr.general;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduBaseRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 百度智能云千帆 PaddleOCR-VL 请求结构
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaddleOCRRequest extends BaiduBaseRequest {

    /**
     * 必选参数: 大模型ID
     */
    private String model;

    /**
     * 所有参数 - 直接透传给渠道
     * 包括：file, fileType, useLayoutDetection, temperature 等所有 PaddleOCR 支持的参数
     * 通过 @JsonAnyGetter 平铺到 JSON 根级别
     */
    @JsonIgnore
    private Map<String, Object> extraParams;

    /**
     * 将 extraParams 中的字段平铺到 JSON 根级别
     */
    @JsonAnyGetter
    public Map<String, Object> getExtraParams() {
        return extraParams;
    }

    /**
     * 重写清理方法，清理 extraParams 中的 file 字段
     */
    @Override
    public void clearLargeData() {
        if (!isCleared()) {
            if (extraParams != null && extraParams.containsKey("file")) {
                extraParams.put("file", null);
            }
            super.clearLargeData();
        }
    }
}
