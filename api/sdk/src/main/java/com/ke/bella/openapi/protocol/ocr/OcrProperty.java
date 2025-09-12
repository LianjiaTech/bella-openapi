package com.ke.bella.openapi.protocol.ocr;

import java.io.Serializable;

import lombok.Data;

/**
 * OCR协议适配器属性基类
 */
@Data
public abstract class OcrProperty implements Serializable {
    private static final long serialVersionUID = 1L;

    // 外部渠道支持的图片输入方式配置
    private boolean supportUrl = true;      // 渠道是否支持URL输入
    private boolean supportBase64 = true;   // 渠道是否支持Base64输入
    private boolean supportBinary = true;   // 渠道是否支持二进制输入
    private boolean supportFormData = true; // 渠道是否支持form-data输入

    // 编码类型配置
    private String encodingType;            // 编码类型
}
