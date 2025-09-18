package com.ke.bella.openapi.protocol.ocr.util;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ke.bella.file.api.FileApiClient;
import com.ke.bella.file.api.config.FileApiProperties;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.common.exception.BizParamCheckException;

/**
 * 图片格式转换工具类
 * 提供各种图片格式转换方法，支持多个OCR渠道使用
 */
@Component
public class ImageConverter {

    @Autowired
    private FileApiProperties fileApiProperties;

    public byte[] convertBase64toBinary(String base64) {
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

        return Base64.getDecoder().decode(cleanBase64);
    }

    public byte[] convertFileIdToBinary(String fileId) {
        if(!StringUtils.hasText(fileId)) {
            throw new BizParamCheckException("文件ID不能为空");
        }

        if(fileApiProperties == null || !StringUtils.hasText(fileApiProperties.getUrl())) {
            throw new BizParamCheckException("文件服务未配置");
        }
        FileApiClient fileApiClient = FileApiClient.getInstance(fileApiProperties.getUrl());
        String apikey = EndpointContext.getApikey().getCode();
        byte[] imageBytes;
        try {
            imageBytes = fileApiClient.getContent(fileId, apikey);
        } catch (Exception e) {
            throw new BizParamCheckException("获取文件失败: " + fileId);
        }
        return imageBytes;
    }
}
