package com.ke.bella.openapi.protocol.ocr.general;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.ocr.BaiduOcrProperty;
import com.ke.bella.openapi.protocol.ocr.ImageRetrievalService;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.util.ImageConverter;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 百度通用文字识别OCR适配器
 */
@Slf4j
@Component("baiduGeneral")
public class BaiduAdaptor implements GeneralAdaptor<BaiduOcrProperty> {

    private static final String ERROR_CODE_SUCCESS = "0";
    private static final String BAIDU_OCR_ERROR_TYPE = "BAIDU_OCR_ERROR";

    /**
     * 百度OCR错误码阈值：大于等于此值为用户参数错误(400)，小于此值为百度服务问题(500)
     */
    private static final int ERROR_CODE_THRESHOLD = 216100;

    @Autowired
    private ImageRetrievalService imageRetrievalService;

    @Override
    public String getDescription() {
        return "百度通用OCR协议";
    }

    @Override
    public Class<BaiduOcrProperty> getPropertyClass() {
        return BaiduOcrProperty.class;
    }

    @Override
    public OcrGeneralResponse general(OcrRequest request, String url, BaiduOcrProperty property) {
        BaiduRequest baiduRequest = requestConvert(request);
        Request httpRequest = buildRequest(baiduRequest, url, property);
        clearLargeData(request, baiduRequest);
        BaiduResponse baiduResponse = HttpUtils.httpRequest(httpRequest, BaiduResponse.class);
        return responseConvert(baiduResponse);
    }

    /**
     * 请求转换：将统一的OcrRequest转换为百度API格式
     */
    private BaiduRequest requestConvert(OcrRequest request) {
        BaiduRequest.BaiduRequestBuilder builder = BaiduRequest.builder();

        if(StringUtils.hasText(request.getImageUrl())) {
            builder.url(request.getImageUrl());
        } else {
            String base64Data;
            if(StringUtils.hasText(request.getImageBase64())) {
                base64Data = ImageConverter.cleanBase64DataHeader(request.getImageBase64());
            } else {
                byte[] imageData = imageRetrievalService.getImageFromFileId(request.getFileId());
                base64Data = Base64.getEncoder().encodeToString(imageData);
            }
            builder.image(base64Data);
        }
        return builder.build();
    }

    /**
     * 构建HTTP请求
     */
    private Request buildRequest(BaiduRequest request, String url, BaiduOcrProperty property) {
        RequestBody formBody = buildFormBody(request);
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(formBody);
        return builder.build();
    }

    /**
     * 构建表单数据，将BaiduRequest转换为FormBody
     */
    private RequestBody buildFormBody(BaiduRequest request) {
        FormBody.Builder builder = new FormBody.Builder();

        // 图片参数二选一
        if(StringUtils.hasText(request.getUrl())) {
            builder.add("url", request.getUrl());
        } else if(StringUtils.hasText(request.getImage())) {
            builder.add("image", request.getImage());
        }

        // PDF相关参数
        if(StringUtils.hasText(request.getPdfFile())) {
            builder.add("pdf_file", request.getPdfFile());
        }
        if(StringUtils.hasText(request.getPdfFileNum())) {
            builder.add("pdf_file_num", request.getPdfFileNum());
        }

        // OFD相关参数
        if(StringUtils.hasText(request.getOfdFile())) {
            builder.add("ofd_file", request.getOfdFile());
        }
        if(StringUtils.hasText(request.getOfdFileNum())) {
            builder.add("ofd_file_num", request.getOfdFileNum());
        }

        // 可选参数（使用默认值）
        if(StringUtils.hasText(request.getLanguageType())) {
            builder.add("language_type", request.getLanguageType());
        }
        if(StringUtils.hasText(request.getDetectDirection())) {
            builder.add("detect_direction", request.getDetectDirection());
        }
        if(StringUtils.hasText(request.getDetectLanguage())) {
            builder.add("detect_language", request.getDetectLanguage());
        }
        if(StringUtils.hasText(request.getParagraph())) {
            builder.add("paragraph", request.getParagraph());
        }
        if(StringUtils.hasText(request.getProbability())) {
            builder.add("probability", request.getProbability());
        }

        return builder.build();
    }

    /**
     * 响应转换：将百度API响应转换为统一的OcrGeneralResponse
     */
    private OcrGeneralResponse responseConvert(BaiduResponse baiduResponse) {
        // 检查百度API返回的错误
        if(hasError(baiduResponse)) {
            return buildErrorResponse(baiduResponse);
        }

        // 构建成功响应
        OcrGeneralResponse.OcrGeneralResponseBuilder builder = OcrGeneralResponse.builder();

        // 设置请求ID
        if(baiduResponse.getLogId() != null) {
            builder.requestId(String.valueOf(baiduResponse.getLogId()));
        }

        // 转换识别结果
        if(baiduResponse.getWordsResult() != null && !baiduResponse.getWordsResult().isEmpty()) {
            List<String> wordsList = baiduResponse.getWordsResult().stream()
                    .map(BaiduResponse.WordsResult::getWords)
                    .collect(Collectors.toList());

            OcrGeneralResponse.GeneralData generalData = OcrGeneralResponse.GeneralData.builder()
                    .words(wordsList)
                    .build();
            builder.data(generalData);
        }

        return builder.build();
    }

    /**
     * 检查百度API是否返回错误
     */
    private boolean hasError(BaiduResponse baiduResponse) {
        return StringUtils.hasText(baiduResponse.getErrorCode()) &&
                !ERROR_CODE_SUCCESS.equals(baiduResponse.getErrorCode());
    }

    /**
     * 构建错误响应
     */
    private OcrGeneralResponse buildErrorResponse(BaiduResponse baiduResponse) {
        int errorCode = Integer.parseInt(baiduResponse.getErrorCode());
        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .code(baiduResponse.getErrorCode())
                .message(baiduResponse.getErrorMsg())
                .type(BAIDU_OCR_ERROR_TYPE)
                .httpCode(errorCode >= ERROR_CODE_THRESHOLD ? HttpStatus.BAD_REQUEST.value() : HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
        return OcrGeneralResponse.builder().error(error).build();
    }
}
