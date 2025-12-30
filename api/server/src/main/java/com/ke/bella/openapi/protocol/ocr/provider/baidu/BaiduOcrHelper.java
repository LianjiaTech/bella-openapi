package com.ke.bella.openapi.protocol.ocr.provider.baidu;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.ocr.ImageRetrievalService;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.util.ImageConverter;

import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.RequestBody;

@Slf4j
@Service
public class BaiduOcrHelper {

    private static final String ERROR_CODE_SUCCESS = "0";

    private static final String BAIDU_OCR_ERROR_TYPE = "BAIDU_OCR_ERROR";

    private static final int ERROR_CODE_THRESHOLD = 216100;

    @Autowired
    private ImageRetrievalService imageRetrievalService;

    public <T extends BaiduBaseRequest> T requestConvert(OcrRequest request, T baiduRequest) {
        if(StringUtils.hasText(request.getImageUrl())) {
            baiduRequest.setUrl(request.getImageUrl());
        } else {
            String base64Data;
            if(StringUtils.hasText(request.getImageBase64())) {
                base64Data = ImageConverter.cleanBase64DataHeader(request.getImageBase64());
            } else {
                byte[] imageData = imageRetrievalService.getImageFromFileId(request.getFileId());
                base64Data = Base64.getEncoder().encodeToString(imageData);
            }
            baiduRequest.setImage(base64Data);
        }
        return baiduRequest;
    }

    public RequestBody buildCommonFormBody(
            BaiduBaseRequest request,
            FormBodyBuilder additionalFields) {
        FormBody.Builder builder = new FormBody.Builder();

        if(StringUtils.hasText(request.getUrl())) {
            builder.add("url", request.getUrl());
        } else if(StringUtils.hasText(request.getImage())) {
            builder.add("image", request.getImage());
        }

        if(additionalFields != null) {
            additionalFields.addFields(builder);
        }
        return builder.build();
    }

    public <T extends BaiduBaseResponse> boolean hasError(T response) {
        return StringUtils.hasText(response.getErrorCode())
                && !ERROR_CODE_SUCCESS.equals(response.getErrorCode());
    }

    public <T extends BaiduBaseResponse, R> R buildErrorResponse(
            T baiduResponse,
            ErrorResponseBuilder<R> builder) {
        int errorCode = Integer.parseInt(baiduResponse.getErrorCode());

        int httpCode = errorCode >= ERROR_CODE_THRESHOLD
                ? HttpStatus.BAD_REQUEST.value()
                : HttpStatus.INTERNAL_SERVER_ERROR.value();

        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .code(baiduResponse.getErrorCode())
                .message(baiduResponse.getErrorMsg())
                .type(BAIDU_OCR_ERROR_TYPE)
                .httpCode(httpCode)
                .build();

        return builder.build(error);
    }

    @FunctionalInterface
    public interface ErrorResponseBuilder<R> {
        R build(OpenapiResponse.OpenapiError error);
    }
}
