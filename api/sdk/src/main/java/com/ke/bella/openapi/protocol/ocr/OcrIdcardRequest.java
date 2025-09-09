package com.ke.bella.openapi.protocol.ocr;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.UserRequest;

import lombok.Data;

/**
 * OCR身份证识别请求
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class OcrIdcardRequest implements UserRequest, Serializable {
    private static final long serialVersionUID = 1L;

    private String user;                    // 用户标识
    private String model;                   // 模型名称，必选

    // 三选一：图片输入方式
    private String image_base64;            // Base64编码的图片
    private String image_url;               // 图片URL
    private String file_id;                 // 文件服务中的文件ID
}
