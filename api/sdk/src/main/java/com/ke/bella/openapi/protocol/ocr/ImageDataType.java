package com.ke.bella.openapi.protocol.ocr;

/**
 * OCR图片数据类型枚举
 * 包含用户输入类型和适配器内部转换类型
 */
public enum ImageDataType {
    // 用户输入类型
    /**
     * 图片URL地址
     */
    URL,

    /**
     * Base64编码的图片数据
     */
    BASE64,

    /**
     * 文件ID（来自文件服务）
     */
    FILE_ID,

    // 适配器内部转换类型（用于与外部渠道适配）
    /**
     * 二进制图片数据
     */
    BINARY,

    /**
     * form-data格式的图片数据
     */
    FORM_DATA
}
