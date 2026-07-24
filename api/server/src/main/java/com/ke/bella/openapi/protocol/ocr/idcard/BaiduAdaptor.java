package com.ke.bella.openapi.protocol.ocr.idcard;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.BaiduOcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduOcrHelper;
import com.ke.bella.openapi.protocol.ocr.util.DateFormatter;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 百度OCR适配器
 */
@Slf4j
@Component("baiduIdcard")
public class BaiduAdaptor implements IdcardAdaptor<BaiduOcrProperty> {

    private static final String FRONT_SIDE = "front";
    private static final String IMAGE_STATUS_REVERSED = "reversed_side";

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
    private BaiduOcrHelper baiduOcrHelper;

    @Override
    public String getDescription() {
        return "百度OCR身份证识别协议";
    }

    @Override
    public Class<BaiduOcrProperty> getPropertyClass() {
        return BaiduOcrProperty.class;
    }

    @Override
    public OcrIdcardResponse idcard(OcrRequest request, String url, BaiduOcrProperty property) {
        BaiduRequest baiduRequest = baiduOcrHelper.requestConvert(request, BaiduRequest.builder().build());
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(buildFormBody(baiduRequest))
                .build();
        clearLargeData(request, baiduRequest);
        BaiduResponse baiduResponse = HttpUtils.httpRequest(httpRequest, BaiduResponse.class);
        return responseConvert(baiduResponse, baiduRequest);
    }

    /**
     * 构建表单数据，将BaiduOcrIdcardRequest转换为FormBody
     */
    private RequestBody buildFormBody(BaiduRequest request) {
        return baiduOcrHelper.buildCommonFormBody(request, builder -> {
            // 身份证特定的必需参数
            builder.add("id_card_side", request.getIdCardSide());

            // 身份证特定的可选参数
            builder.add("detect_ps", request.getDetectPs())
                    .add("detect_risk", request.getDetectRisk())
                    .add("detect_quality", request.getDetectQuality())
                    .add("detect_photo", request.getDetectPhoto())
                    .add("detect_card", request.getDetectCard())
                    .add("detect_direction", request.getDetectDirection())
                    .add("detect_screenshot", request.getDetectScreenshot());
        });
    }

    /**
     * 转换百度响应为统一格式
     */
    private OcrIdcardResponse responseConvert(BaiduResponse baiduResponse, BaiduRequest baiduRequest) {
        // 检查百度API返回的错误
        if(baiduOcrHelper.hasError(baiduResponse)) {
            return buildErrorResponse(baiduResponse);
        }

        // 构建成功响应
        OcrIdcardResponse response = new OcrIdcardResponse();
        response.setRequest_id(String.valueOf(baiduResponse.getLogId()));

        // 确定身份证面并提取数据
        OcrIdcardResponse.IdCardSide actualSide = determineActualSide(baiduRequest, baiduResponse);
        response.setSide(actualSide);
        extractIdCardData(response, baiduResponse, actualSide);

        return response;
    }

    /**
     * 根据身份证面提取对应的数据
     */
    private void extractIdCardData(OcrIdcardResponse response, BaiduResponse baiduResponse,
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
    private OcrIdcardResponse.PortraitData extractPortraitData(BaiduResponse baiduResponse) {
        OcrIdcardResponse.PortraitData.PortraitDataBuilder builder = OcrIdcardResponse.PortraitData.builder();

        if(baiduResponse.getWordsResult() != null) {
            Map<String, BaiduResponse.WordResult> wordsResult = baiduResponse.getWordsResult();

            // 提取各字段信息
            builder.name(getFieldValue(wordsResult, FIELD_NAME))
                    .sex(getFieldValue(wordsResult, FIELD_SEX))
                    .nationality(getFieldValue(wordsResult, FIELD_NATIONALITY))
                    .idcard_number(getFieldValue(wordsResult, FIELD_ID_NUMBER))
                    .address(getFieldValue(wordsResult, FIELD_ADDRESS))
                    .birth_date(DateFormatter.formatDate(getFieldValue(wordsResult, FIELD_BIRTH_DATE), "yyyyMMdd", "yyyy年M月d日"));
        }

        return builder.build();
    }

    /**
     * 提取身份证背面（国徽面）数据
     */
    private OcrIdcardResponse.NationalEmblemData extractNationalEmblemData(BaiduResponse baiduResponse) {
        OcrIdcardResponse.NationalEmblemData.NationalEmblemDataBuilder builder = OcrIdcardResponse.NationalEmblemData.builder();

        if(baiduResponse.getWordsResult() != null) {
            Map<String, BaiduResponse.WordResult> wordsResult = baiduResponse.getWordsResult();

            // 提取各字段信息
            builder.issue_authority(getFieldValue(wordsResult, FIELD_ISSUE_AUTHORITY))
                    .valid_date_start(DateFormatter.formatDate(getFieldValue(wordsResult, FIELD_ISSUE_DATE), "yyyyMMdd", "yyyy.MM.dd"))
                    .valid_date_end(DateFormatter.formatDate(getFieldValue(wordsResult, FIELD_EXPIRE_DATE), "yyyyMMdd", "yyyy.MM.dd"));
        }

        return builder.build();
    }

    /**
     * 从识别结果中获取字段值
     */
    private String getFieldValue(Map<String, BaiduResponse.WordResult> wordsResult, String fieldName) {
        BaiduResponse.WordResult wordResult = wordsResult.get(fieldName);
        return wordResult != null ? wordResult.getWords() : null;
    }

    private OcrIdcardResponse.IdCardSide determineActualSide(BaiduRequest baiduRequest,
            BaiduResponse baiduResponse) {
        String expectedSide = baiduRequest.getIdCardSide();
        String imageStatus = baiduResponse.getImageStatus();

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
    private OcrIdcardResponse buildErrorResponse(BaiduResponse baiduResponse) {
        return baiduOcrHelper.buildErrorResponse(baiduResponse,
                error -> OcrIdcardResponse.builder().error(error).build());
    }

}
