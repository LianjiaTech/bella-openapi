package com.ke.bella.openapi.protocol.ocr.provider.yidao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.ocr.ImageRetrievalService;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.YidaoOcrProperty;
import com.ke.bella.openapi.protocol.ocr.util.ImageConverter;

import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@Service
public class YidaoOcrHelper {

    private static final int SUCCESS_CODE = 0;
    private static final String YIDAO_OCR_ERROR_TYPE = "YIDAO_OCR_ERROR";
    // U+2014 (Em Dash "—") and U+002D (Hyphen-Minus "-")
    private static final String VALID_DATE_SEPARATOR_REGEX = "[—\\-]";

    @Autowired
    private ImageRetrievalService imageRetrievalService;

    public YidaoRequest requestConvert(OcrRequest request, YidaoOcrProperty property) {
        try {
            YidaoRequest.YidaoRequestBuilder builder = YidaoRequest.builder()
                    .appKey(property.getAuth().getApiKey())
                    .appSecret(property.getAuth().getSecret());

            if(StringUtils.hasText(request.getImageBase64())) {
                String imageBase64 = ImageConverter.cleanBase64DataHeader(request.getImageBase64());
                builder.imageBase64(imageBase64);
            } else if(StringUtils.hasText(request.getImageUrl())) {
                builder.imageUrl(request.getImageUrl());
            } else {
                byte[] imageData = imageRetrievalService.getImageFromFileId(request.getFileId());
                builder.imageBinary(imageData);
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to retrieve image data", e);
            throw new IllegalStateException("Failed to retrieve image data", e);
        }
    }

    public Request buildRequest(YidaoRequest request, String url) {
        if(request.getImageBinary() != null) {
            MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("app_key", request.getAppKey())
                    .addFormDataPart("app_secret", request.getAppSecret())
                    .addFormDataPart("image_binary", "image.jpg",
                            RequestBody.create(MediaType.parse("image/jpeg"), request.getImageBinary()));

            return new Request.Builder()
                    .url(url)
                    .post(bodyBuilder.build())
                    .build();
        } else {
            FormBody.Builder bodyBuilder = new FormBody.Builder()
                    .add("app_key", request.getAppKey())
                    .add("app_secret", request.getAppSecret());

            if(StringUtils.hasText(request.getImageBase64())) {
                bodyBuilder.add("image_base64", request.getImageBase64());
            } else if(StringUtils.hasText(request.getImageUrl())) {
                bodyBuilder.add("image_url", request.getImageUrl());
            }

            return new Request.Builder()
                    .url(url)
                    .post(bodyBuilder.build())
                    .build();
        }
    }

    public <T extends YidaoBaseResponse<?>> boolean hasError(T response) {
        return response.getErrorCode() == null || response.getErrorCode() != SUCCESS_CODE;
    }

    public <T extends YidaoBaseResponse<?>, R> R buildErrorResponse(
            T yidaoResponse,
            ErrorResponseBuilder<R> builder) {
        String errorMessage = yidaoResponse.getDescription() != null
                ? yidaoResponse.getDescription()
                : "Unknown error";

        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .code(String.valueOf(yidaoResponse.getErrorCode()))
                .message(errorMessage)
                .type(YIDAO_OCR_ERROR_TYPE)
                .httpCode(HttpStatus.BAD_REQUEST.value())
                .build();

        return builder.build(error);
    }

    public String getFieldWords(YidaoBaseResponse.FieldData fieldData) {
        return fieldData != null ? fieldData.getWords() : null;
    }

    public String[] parseValidDate(String validDate) {
        if(!StringUtils.hasText(validDate)) {
            return new String[] { "", "" };
        }

        String[] parts = validDate.split(VALID_DATE_SEPARATOR_REGEX);
        if(parts.length == 2) {
            return new String[] { parts[0].trim(), parts[1].trim() };
        }

        log.warn("Unexpected valid date format: {}", validDate);
        return new String[] { validDate, "" };
    }

    @FunctionalInterface
    public interface ErrorResponseBuilder<R> {
        R build(OpenapiResponse.OpenapiError error);
    }
}
