package com.ke.bella.openapi.protocol.ocr.provider.ke;

import java.util.Base64;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.ocr.idcard.OcrIdcardResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.ocr.ImageRetrievalService;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.util.ImageConverter;

@Service
public class KeOcrHelper {
    private static final String KE_ERROR_TYPE = "KE_OCR_ERROR";
    private static final int SUCCESS_CODE = 0;

    @Autowired
    private ImageRetrievalService imageRetrievalService;

    public KeRequest requestConvert(OcrRequest request) {
        KeRequest.RequestData.RequestDataBuilder builder = KeRequest.RequestData.builder();
        if(StringUtils.hasText(request.getImageUrl())) {
            builder.imageUrl(request.getImageUrl());
        } else {
            String base64Data;
            if(StringUtils.hasText(request.getImageBase64())) {
                base64Data = ImageConverter.cleanBase64DataHeader(request.getImageBase64());
            } else {
                byte[] imageData = imageRetrievalService.getImageFromFileId(request.getFileId());
                base64Data = Base64.getEncoder().encodeToString(imageData);
            }
            builder.imageBase64(base64Data);
        }
        String requestId = BellaContext.getRequestId();
        return KeRequest.builder()
                .requestId(requestId)
                .data(builder.build())
                .build();
    }

    public boolean hasError(KeResponse keResponse) {
        return keResponse.getCode() != SUCCESS_CODE;
    }

    public OcrIdcardResponse buildErrorResponse(KeResponse keResponse) {
        int code = keResponse.getCode();
        int httpCode = determineHttpCode(code);

        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .code(String.valueOf(code))
                .message(keResponse.getMessage())
                .type(KE_ERROR_TYPE)
                .httpCode(httpCode)
                .build();
        return OcrIdcardResponse.builder()
                .error(error)
                .build();
    }

    /**
     * 从KeResponse的structuredResult中查找指定key的值
     *
     * @param keResponse Ke OCR响应对象
     * @param key 要查找的字段key（enKeyName）
     * @return 字段值，如果不存在返回null
     */
    public String findValueByKey(KeResponse keResponse, String key) {
        if (keResponse == null ||
                keResponse.getResult() == null ||
            keResponse.getResult().getStructuredResult() == null) {
            return null;
        }
        return keResponse.getResult().getStructuredResult().stream()
                .filter(item -> key.equals(item.getEnKeyName()))
                .map(KeResponse.StructuredResult::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据错误码范围确定HTTP状态码
     *
     * 错误码范围说明：
     * - 100xx: 参数错误 (400)
     * - 201xx: 图片问题 (400)
     * - 301xx-309xx: 模型推理失败，可重试 (503)
     * - 401xx-407xx: 服务端配置或代码问题 (500)
     * - 501xx-502xx: 请求格式问题 (400)
     * - 600xx: Triton服务问题，可重试 (503)
     * - 99999: 未知错误 (500)
     * - 其他: 未知错误 (500)
     */
    private int determineHttpCode(int code) {
        if (code >= 10000 && code <= 10099) {
            return 400; // 参数错误
        } else if (code >= 20100 && code <= 20199) {
            return 400; // 图片问题
        } else if ((code >= 30100 && code <= 30999)) {
            return 503; // 模型推理失败，可重试
        } else if (code >= 40100 && code <= 40799) {
            return 500; // 服务端问题
        } else if (code >= 50100 && code <= 50299) {
            return 400; // 请求格式问题
        } else if (code >= 60000 && code <= 60099) {
            return 503; // Triton服务问题，可重试
        } else if (code == 99999) {
            return 500; // 未知错误
        } else {
            return 500; // 默认服务端错误
        }
    }
}
