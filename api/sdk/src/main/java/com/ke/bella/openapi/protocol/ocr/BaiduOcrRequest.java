package com.ke.bella.openapi.protocol.ocr;
import java.util.HashMap;

import lombok.Builder;
import lombok.Data;

/**
 * 百度OCR身份证识别内部请求对象
 */
@Data
@Builder
public class BaiduOcrRequest {
    // 百度SDK要求的参数
	private byte[] imageData;           // 图片二进制数据
	private String imagePath;           // 图片本地路径
	private String imageUrl;            // 图片URL
    private String idCardSide;          // 身份证面："front"(正面) 或 "back"(背面)
    private HashMap<String, String> options; // 可选参数配置
	private ImageDataType inputType;     // 输入数据类型
}
