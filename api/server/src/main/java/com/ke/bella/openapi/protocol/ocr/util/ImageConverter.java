package com.ke.bella.openapi.protocol.ocr.util;

import java.util.Base64;

/**
 * 图片格式转换工具类
 * 提供各种图片格式转换方法，支持多个OCR渠道使用
 */
public class ImageConverter {

    public static byte[] convertBase64toBinary(String base64) {
        if(base64 == null || base64.isEmpty()) {
            return new byte[0];
        }

        String cleanBase64 = base64;
        if(base64.startsWith("data:")) {
            int commaIndex = base64.indexOf(",");
            if(commaIndex > 0) {
                cleanBase64 = base64.substring(commaIndex + 1);
            }
        }

        try {
            return Base64.getDecoder().decode(cleanBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 format: " + e.getMessage(), e);
        }
    }
}
