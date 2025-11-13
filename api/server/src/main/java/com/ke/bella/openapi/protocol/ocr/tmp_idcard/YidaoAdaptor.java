package com.ke.bella.openapi.protocol.ocr.tmp_idcard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.ocr.ImageRetrievalService;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.YidaoBaseResponse;
import com.ke.bella.openapi.protocol.ocr.YidaoOcrProperty;
import com.ke.bella.openapi.protocol.ocr.YidaoRequest;
import com.ke.bella.openapi.protocol.ocr.util.ImageConverter;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@Component("yidaoTmpIdcard")
public class YidaoAdaptor implements TmpIdcardAdaptor<YidaoOcrProperty> {

    private static final int SUCCESS_ERROR_CODE = 0;
    private static final String YIDAO_OCR_ERROR_TYPE = "YIDAO_OCR_ERROR";
    private static final String VALID_DATE_SEPARATOR = "—"; // U+2014，Em Dash


    @Autowired
    private ImageRetrievalService imageRetrievalService;

    @Override
    public String getDescription() {
        return "易道OCR临时身份证协议";
    }

    @Override
    public Class<YidaoOcrProperty> getPropertyClass() {
        return YidaoOcrProperty.class;
    }

    @Override
    public OcrTmpIdcardResponse tmpIdcard(OcrRequest request, String url, YidaoOcrProperty property) {
        YidaoRequest yidaoRequest = requestConvert(request, property);
        Request httpRequest = buildRequest(yidaoRequest, url);
        clearLargeData(request, yidaoRequest);
        YidaoResponse yidaoResponse = HttpUtils.httpRequest(httpRequest, YidaoResponse.class);
        return responseConvert(yidaoResponse);
    }

    private YidaoRequest requestConvert(OcrRequest request, YidaoOcrProperty property) {
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

    private Request buildRequest(YidaoRequest request, String url) {
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

    private OcrTmpIdcardResponse responseConvert(YidaoResponse yidaoResponse) {
        if(hasError(yidaoResponse)) {
            return buildErrorResponse(yidaoResponse);
        }

        OcrTmpIdcardResponse response = new OcrTmpIdcardResponse();
        response.setRequest_id(yidaoResponse.getRequestId());

        YidaoResponse.ResultData result = yidaoResponse.getResult();
        if(result != null) {
            String validDate = getFieldWords(result.getValid());
            String[] dates = parseValidDate(validDate);

            OcrTmpIdcardResponse.TmpIdcardData data = OcrTmpIdcardResponse.TmpIdcardData.builder()
                    .name(getFieldWords(result.getName()))
                    .sex(getFieldWords(result.getGender()))
                    .nationality(getFieldWords(result.getNationality()))
                    .birth_date(getFieldWords(result.getBirthdate()))
                    .address(getFieldWords(result.getAddress()))
                    .idcard_number(getFieldWords(result.getIdno()))
                    .issue_authority(getFieldWords(result.getIssued()))
                    .valid_date_start(dates[0])
                    .valid_date_end(dates[1])
                    .build();
            response.setData(data);
        }

        return response;
    }

    private boolean hasError(YidaoResponse yidaoResponse) {
        return yidaoResponse.getErrorCode() == null || yidaoResponse.getErrorCode() != SUCCESS_ERROR_CODE;
    }

    private OcrTmpIdcardResponse buildErrorResponse(YidaoResponse yidaoResponse) {
        String errorMessage = yidaoResponse.getDescription() != null
                ? yidaoResponse.getDescription()
                : "Unknown error";

        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .code(String.valueOf(yidaoResponse.getErrorCode()))
                .message(errorMessage)
                .type(YIDAO_OCR_ERROR_TYPE)
                .httpCode(HttpStatus.BAD_REQUEST.value())
                .build();

        return OcrTmpIdcardResponse.builder().error(error).build();
    }

    private String getFieldWords(YidaoBaseResponse.FieldData fieldData) {
        return fieldData != null ? fieldData.getWords() : null;
    }

    private String[] parseValidDate(String validDate) {
        if(validDate == null) {
            return new String[] { null, null };
        }

        String[] parts = validDate.split(VALID_DATE_SEPARATOR);
        if(parts.length == 2) {
            return new String[] { parts[0].trim(), parts[1].trim() };
        }

        return new String[] { validDate, null };
    }
}
