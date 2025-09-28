package com.ke.bella.openapi.protocol.ocr;

import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.ocr.idcard.BaiduOcrIdcardRequest;
import com.ke.bella.openapi.protocol.ocr.idcard.BaiduOcrIdcardResponse;
import com.ke.bella.openapi.protocol.ocr.idcard.OcrIdcardRequest;
import com.ke.bella.openapi.protocol.ocr.idcard.OcrIdcardResponse;
import com.ke.bella.openapi.protocol.ocr.util.ImageConverter;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 百度OCR适配器
 */
@Slf4j
@Component("baiduIdcard")
public class BaiduIdcardAdaptor implements OcrIdcardAdaptor<BaiduOcrProperty> {

    private static final String FRONT_SIDE = "front";
    private static final String IMAGE_STATUS_REVERSED = "reversed_side";
    private static final String ERROR_CODE_SUCCESS = "0";
    private static final String BAIDU_OCR_ERROR_TYPE = "BAIDU_OCR_ERROR";

    /**
     * 百度OCR错误码阈值：大于等于此值为用户参数错误(400)，小于此值为百度服务问题(500)
     */
    private static final int ERROR_CODE_THRESHOLD = 216100;

    // 身份证字段常量
    private static final String FIELD_NAME = "姓名";
    private static final String FIELD_SEX = "性别";
    private static final String FIELD_NATIONALITY = "民族";
    private static final String FIELD_ID_NUMBER = "公民身份号码";
    private static final String FIELD_ADDRESS = "住址";
    private static final String FIELD_BIRTH_DATE = "出生";
    private static final String FIELD_ISSUE_AUTHORITY = "签发机关";
    private static final String FIELD_ISSUE_DATE = "签发日期";
    private static final String FIELD_EXPIRE_DATE = "失效日期";

    @Autowired
    private ImageRetrievalService imageRetrievalService;

    @Override
    public String getDescription() {
        return "百度OCR身份证识别协议";
    }

    @Override
    public Class<BaiduOcrProperty> getPropertyClass() {
        return BaiduOcrProperty.class;
    }

    @Override
    public OcrIdcardResponse idcard(OcrIdcardRequest request, String url, BaiduOcrProperty property) {
        BaiduOcrIdcardRequest baiduOcrIdcardRequest = requestConvert(request);
        Request httpRequest = buildRequest(baiduOcrIdcardRequest, url, property);
        BaiduOcrIdcardResponse baiduOcrIdcardResponse = HttpUtils.httpRequest(httpRequest, BaiduOcrIdcardResponse.class);
        return responseConvert(baiduOcrIdcardResponse, baiduOcrIdcardRequest);
    }

    private BaiduOcrIdcardRequest requestConvert(OcrIdcardRequest request) {
        BaiduOcrIdcardRequest.BaiduOcrIdcardRequestBuilder builder = BaiduOcrIdcardRequest.builder();
        // 处理图片数据：url和image二选一
        if(StringUtils.hasText(request.getImageUrl())) {
            // 如果有URL，直接设置url参数
            builder.url(request.getImageUrl());
        } else {
            // 处理base64数据或fileId，都设置为image参数
            String base64Data;
            if(StringUtils.hasText(request.getImageBase64())) {
                // 如果有base64数据，先清理数据头
                base64Data = ImageConverter.cleanBase64DataHeader(request.getImageBase64());
            } else {
                // 如果有fileId，先获取文件内容然后转为base64
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
    private Request buildRequest(BaiduOcrIdcardRequest request, String url, BaiduOcrProperty property) {
        RequestBody formBody = buildFormBody(request);
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(formBody);
        return builder.build();
    }

    /**
     * 构建表单数据，将BaiduOcrIdcardRequest转换为FormBody
     */
    private RequestBody buildFormBody(BaiduOcrIdcardRequest request) {
        FormBody.Builder builder = new FormBody.Builder();

        // 必需参数
        builder.add("id_card_side", request.getIdCardSide());

        // 图片参数二选一
        if(StringUtils.hasText(request.getUrl())) {
            builder.add("url", request.getUrl());
        } else if(StringUtils.hasText(request.getImage())) {
            builder.add("image", request.getImage());
        }

        // 可选参数，直接添加（字段已有默认值false）
        builder.add("detect_ps", request.getDetectPs())
                .add("detect_risk", request.getDetectRisk())
                .add("detect_quality", request.getDetectQuality())
                .add("detect_photo", request.getDetectPhoto())
                .add("detect_card", request.getDetectCard())
                .add("detect_direction", request.getDetectDirection())
                .add("detect_screenshot", request.getDetectScreenshot());

        return builder.build();
    }

    /**
     * 转换百度响应为统一格式
     */
    private OcrIdcardResponse responseConvert(BaiduOcrIdcardResponse baiduOcrIdcardResponse, BaiduOcrIdcardRequest baiduOcrIdcardRequest) {
        // 检查百度API返回的错误
        if(hasError(baiduOcrIdcardResponse)) {
            return buildErrorResponse(baiduOcrIdcardResponse);
        }

        // 构建成功响应
        OcrIdcardResponse response = new OcrIdcardResponse();
        response.setRequest_id(String.valueOf(baiduOcrIdcardResponse.getLogId()));

        // 确定身份证面并提取数据
        OcrIdcardResponse.IdCardSide actualSide = determineActualSide(baiduOcrIdcardRequest, baiduOcrIdcardResponse);
        response.setSide(actualSide);
        extractIdCardData(response, baiduOcrIdcardResponse, actualSide);

        return response;
    }

    /**
     * 检查百度API是否返回错误
     */
    private boolean hasError(BaiduOcrIdcardResponse baiduResponse) {
        return StringUtils.hasText(baiduResponse.getErrorCode()) &&
                !ERROR_CODE_SUCCESS.equals(baiduResponse.getErrorCode());
    }

    /**
     * 根据身份证面提取对应的数据
     */
    private void extractIdCardData(OcrIdcardResponse response, BaiduOcrIdcardResponse baiduResponse,
            OcrIdcardResponse.IdCardSide actualSide) {
        if(actualSide == OcrIdcardResponse.IdCardSide.PORTRAIT) {
            response.setData(extractPortraitData(baiduResponse));
        } else {
            response.setData(extractNationalEmblemData(baiduResponse));
        }
    }

    /**
     * 提取身份证正面（肖像面）数据
     */
    private OcrIdcardResponse.PortraitData extractPortraitData(BaiduOcrIdcardResponse baiduResponse) {
        OcrIdcardResponse.PortraitData.PortraitDataBuilder builder = OcrIdcardResponse.PortraitData.builder();

        if(baiduResponse.getWordsResult() != null) {
            Map<String, BaiduOcrIdcardResponse.WordResult> wordsResult = baiduResponse.getWordsResult();

            // 提取各字段信息
            builder.name(getFieldValue(wordsResult, FIELD_NAME))
                    .sex(getFieldValue(wordsResult, FIELD_SEX))
                    .nationality(getFieldValue(wordsResult, FIELD_NATIONALITY))
                    .idcard_number(getFieldValue(wordsResult, FIELD_ID_NUMBER))
                    .address(getFieldValue(wordsResult, FIELD_ADDRESS))
                    .birth_date(formatDate(getFieldValue(wordsResult, FIELD_BIRTH_DATE), "yyyy年M月d日"));
        }

        return builder.build();
    }

    /**
     * 提取身份证背面（国徽面）数据
     */
    private OcrIdcardResponse.NationalEmblemData extractNationalEmblemData(BaiduOcrIdcardResponse baiduResponse) {
        OcrIdcardResponse.NationalEmblemData.NationalEmblemDataBuilder builder = OcrIdcardResponse.NationalEmblemData.builder();

        if(baiduResponse.getWordsResult() != null) {
            Map<String, BaiduOcrIdcardResponse.WordResult> wordsResult = baiduResponse.getWordsResult();

            // 提取各字段信息
            builder.issue_authority(getFieldValue(wordsResult, FIELD_ISSUE_AUTHORITY))
                    .valid_date_start(formatDate(getFieldValue(wordsResult, FIELD_ISSUE_DATE), "yyyy.MM.dd"))
                    .valid_date_end(formatDate(getFieldValue(wordsResult, FIELD_EXPIRE_DATE), "yyyy.MM.dd"));
        }

        return builder.build();
    }

    /**
     * 从识别结果中获取字段值
     */
    private String getFieldValue(Map<String, BaiduOcrIdcardResponse.WordResult> wordsResult, String fieldName) {
        BaiduOcrIdcardResponse.WordResult wordResult = wordsResult.get(fieldName);
        return wordResult != null ? wordResult.getWords() : null;
    }

    private OcrIdcardResponse.IdCardSide determineActualSide(BaiduOcrIdcardRequest baiduOcrIdcardRequest,
            BaiduOcrIdcardResponse baiduOcrIdcardResponse) {
        String expectedSide = baiduOcrIdcardRequest.getIdCardSide();
        String imageStatus = baiduOcrIdcardResponse.getImageStatus();

        // 判断是否为前面（人像面）
        boolean isFrontSide = FRONT_SIDE.equals(expectedSide);

        // 如果image_status为reversed_side，说明检测到的面与期望相反，需要反转结果
        if(IMAGE_STATUS_REVERSED.equals(imageStatus)) {
            isFrontSide = !isFrontSide;
        }

        return isFrontSide ? OcrIdcardResponse.IdCardSide.PORTRAIT : OcrIdcardResponse.IdCardSide.NATIONAL_EMBLEM;
    }

    /**
     * 构建错误响应
     */
    private OcrIdcardResponse buildErrorResponse(BaiduOcrIdcardResponse baiduResponse) {
        int errorCode = Integer.parseInt(baiduResponse.getErrorCode());
        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .code(baiduResponse.getErrorCode())
                .message(baiduResponse.getErrorMsg())
                .type(BAIDU_OCR_ERROR_TYPE)
                .httpCode(errorCode >= ERROR_CODE_THRESHOLD ? HttpStatus.BAD_REQUEST.value() : HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
        return OcrIdcardResponse.builder().error(error).build();
    }

    /**
     * 日期格式转换方法，将"yyyyMMdd"格式转换为指定格式
     */
    private String formatDate(String dateStr, String outputPattern) {

        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd");
            inputFormat.setLenient(false); // 严格模式，避免无效日期被自动修正
            Date date = inputFormat.parse(dateStr.trim());

            SimpleDateFormat outputFormat = new SimpleDateFormat(outputPattern);
            return outputFormat.format(date);
        } catch (Exception e) {
            log.debug("日期格式转换失败: {} -> {}", dateStr, outputPattern, e);
            return ""; // 转换失败时返回空值
        }
    }

}
