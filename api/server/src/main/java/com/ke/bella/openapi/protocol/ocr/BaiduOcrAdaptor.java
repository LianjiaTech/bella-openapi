package com.ke.bella.openapi.protocol.ocr;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baidu.aip.ocr.AipOcr;
import com.ke.bella.file.api.FileApiClient;
import com.ke.bella.file.api.config.FileApiProperties;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.ocr.util.ImageConverter;

import lombok.extern.slf4j.Slf4j;

/**
 * 百度OCR适配器
 */
@Slf4j
@Component("baiduOcr")
public class BaiduOcrAdaptor implements OcrIdcardAdaptor<BaiduOcrProperty> {

    private static final String FRONT_SIDE = "front";
    private static final String IMAGE_STATUS_NORMAL = "normal";
    private static final String IMAGE_STATUS_REVERSED = "reversed_side";
    private static final String ERROR_CODE_FIELD = "error_code";
    private static final String ERROR_MSG_FIELD = "error_msg";
    private static final String ERROR_CODE_SUCCESS = "0";
    private static final String IMAGE_STATUS_FIELD = "image_status";
    private static final String WORDS_RESULT_FIELD = "words_result";
    private static final String WORDS_FIELD = "words";
    private static final String BAIDU_OCR_ERROR_TYPE = "BAIDU_OCR_ERROR";

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
    private FileApiProperties fileApiProperties;

    // AipOcr客户端缓存(单例使用，避免重复获取access_token)
    private final Map<String, AipOcr> clientCache = new ConcurrentHashMap<>();

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
        try {
            AipOcr client = getOrCreateClient(property);
            HashMap<String, String> options = buildRequestOptions(property);
            JSONObject baiduResponse = performOcrRecognition(request, client, options);
            return convertBaiduResponse(baiduResponse, request);
        } catch (Exception e) {
            log.error("百度OCR识别失败", e);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "百度OCR识别失败: " + e.getMessage(), null);
        }
    }

    /**
     * 构建请求选项
     */
    private HashMap<String, String> buildRequestOptions(BaiduOcrProperty property) {
        return new OcrOptionsBuilder()
                .detectDirection(property.getDetectDirection())
                .detectRisk(property.getDetectRisk())
                .detectPhoto(property.getDetectPhoto())
                .detectCard(property.getDetectCard())
                .build();
    }

    /**
     * 执行OCR识别
     */
    private JSONObject performOcrRecognition(OcrIdcardRequest request, AipOcr client, HashMap<String, String> options) {
        // 统一使用front参数，百度会通过image_status返回实际的正反面信息
        String idCardSide = getIdCardSideForBaiduApi(request);

        if(StringUtils.hasText(request.getImageUrl())) {
            return client.idcardUrl(request.getImageUrl(), idCardSide, options);
        } else {
            byte[] imageData = getImageData(request);
            return client.idcard(imageData, idCardSide, options);
        }
    }

    /**
     * 获取图片数据
     */
    private byte[] getImageData(OcrIdcardRequest request) {
        if(StringUtils.hasText(request.getFileId())) {
            return getImageFromFileId(request.getFileId());
        } else if(StringUtils.hasText(request.getImageBase64())) {
            return ImageConverter.convertBase64toBinary(request.getImageBase64());
        } else {
            throw new BizParamCheckException("渠道不支持的图片数据");
        }
    }

    /**
     * 从文件ID获取图片数据
     */
    private byte[] getImageFromFileId(String fileId) {
        FileApiClient fileApiClient = FileApiClient.getInstance(fileApiProperties.getUrl());
        String apikey = EndpointContext.getApikey().getCode();
        return fileApiClient.getContent(fileId, apikey);
    }

    /**
     * OCR选项构建器
     */
    private static class OcrOptionsBuilder {
        private final HashMap<String, String> options = new HashMap<>();

        public OcrOptionsBuilder detectDirection(Boolean detectDirection) {
            if(detectDirection != null) {
                options.put("detect_direction", detectDirection.toString());
            }
            return this;
        }

        public OcrOptionsBuilder detectRisk(Boolean detectRisk) {
            if(detectRisk != null) {
                options.put("detect_risk", detectRisk.toString());
            }
            return this;
        }

        public OcrOptionsBuilder detectPhoto(Boolean detectPhoto) {
            if(detectPhoto != null) {
                options.put("detect_photo", detectPhoto.toString());
            }
            return this;
        }

        public OcrOptionsBuilder detectCard(Boolean detectCard) {
            if(detectCard != null) {
                options.put("detect_card", detectCard.toString());
            }
            return this;
        }

        public HashMap<String, String> build() {
            return options;
        }
    }

    /**
     * 获取或创建AipOcr客户端(单例使用)
     */
    private AipOcr getOrCreateClient(BaiduOcrProperty property) {
        String clientKey = property.getAppId() + ":" + property.getApiKey();
        return clientCache.computeIfAbsent(clientKey, k -> createAipOcr(property));
    }

    /**
     * 创建AipOcr客户端
     */
    private AipOcr createAipOcr(BaiduOcrProperty property) {
        AipOcr client = new AipOcr(property.getAppId(), property.getApiKey(), property.getSecretKey());

        // 设置网络连接参数
        if(property.getConnectionTimeoutMillis() != null) {
            client.setConnectionTimeoutInMillis(property.getConnectionTimeoutMillis());
        }
        if(property.getSocketTimeoutMillis() != null) {
            client.setSocketTimeoutInMillis(property.getSocketTimeoutMillis());
        }

        // 设置代理服务器(如果配置了)
        if(StringUtils.hasText(property.getProxyHost()) && property.getProxyPort() != null) {
            client.setHttpProxy(property.getProxyHost(), property.getProxyPort());
        }

        return client;
    }

    /**
     * 获取身份证面参数
     * 注意：由于无法从请求中判断用户上传的是正面还是反面，
     * 这里统一使用"front"，让百度API自动检测实际的正反面，
     * 然后通过返回的image_status字段来确定实际面信息
     */
    @SuppressWarnings("unused")
    private String getIdCardSideForBaiduApi(OcrIdcardRequest request) {
        // 百度API会自动检测正反面，统一传入front参数
        return FRONT_SIDE;
    }

    /**
     * 转换百度响应为统一格式
     */
    private OcrIdcardResponse convertBaiduResponse(JSONObject baiduResponse, OcrIdcardRequest originalRequest) {
        if(baiduResponse.has(ERROR_CODE_FIELD) && !ERROR_CODE_SUCCESS.equals(baiduResponse.getString(ERROR_CODE_FIELD))) {
            return buildErrorResponse(baiduResponse.getString(ERROR_CODE_FIELD), baiduResponse.getString(ERROR_MSG_FIELD), baiduResponse);
        }
        OcrIdcardResponse response = new OcrIdcardResponse();
        response.setRequest_id(String.valueOf(baiduResponse.getLong("log_id")));
        String imageStatus = baiduResponse.getString(IMAGE_STATUS_FIELD);

        // 根据输入参数和image_status推断实际的身份证面
        OcrIdcardResponse.IdCardSide actualSide = determineActualSide(originalRequest, imageStatus);
        response.setSide(actualSide);

        // 根据实际的身份证面提取对应数据
        if(actualSide == OcrIdcardResponse.IdCardSide.PORTRAIT) {
            response.setData(extractPortraitData(baiduResponse));
        } else {
            response.setData(extractNationalEmblemData(baiduResponse));
        }

        return response;
    }

    private OcrIdcardResponse.IdCardSide determineActualSide(OcrIdcardRequest originalRequest, String imageStatus) {
        String expectedSide = getIdCardSideForBaiduApi(originalRequest);

        // 如果image_status为normal，说明图片面和期望一致
        if(IMAGE_STATUS_NORMAL.equals(imageStatus)) {
            return FRONT_SIDE.equals(expectedSide) ? OcrIdcardResponse.IdCardSide.PORTRAIT : OcrIdcardResponse.IdCardSide.NATIONAL_EMBLEM;
        }

        // 如果image_status为reversed_side，说明图片面和期望相反
        if(IMAGE_STATUS_REVERSED.equals(imageStatus)) {
            return FRONT_SIDE.equals(expectedSide) ? OcrIdcardResponse.IdCardSide.NATIONAL_EMBLEM : OcrIdcardResponse.IdCardSide.PORTRAIT;
        }

        // 默认情况下，按照期望的面返回
        return FRONT_SIDE.equals(expectedSide) ? OcrIdcardResponse.IdCardSide.PORTRAIT : OcrIdcardResponse.IdCardSide.NATIONAL_EMBLEM;
    }

    /**
     * 提取人像面数据
     */
    private OcrIdcardResponse.PortraitData extractPortraitData(JSONObject baiduResponse) {
        OcrIdcardResponse.PortraitData data = new OcrIdcardResponse.PortraitData();

        if(baiduResponse.has(WORDS_RESULT_FIELD)) {
            JSONObject wordsResult = baiduResponse.getJSONObject(WORDS_RESULT_FIELD);

            // 提取各字段
            data.setName(extractFieldValue(wordsResult, FIELD_NAME));
            data.setSex(extractFieldValue(wordsResult, FIELD_SEX));
            data.setNationality(extractFieldValue(wordsResult, FIELD_NATIONALITY));
            data.setIdcard_number(extractFieldValue(wordsResult, FIELD_ID_NUMBER));
            data.setAddress(extractFieldValue(wordsResult, FIELD_ADDRESS));
            data.setBirth_date(formatBirthDate(extractFieldValue(wordsResult, FIELD_BIRTH_DATE)));
        }

        return data;
    }

    /**
     * 提取国徽面数据
     */
    private OcrIdcardResponse.NationalEmblemData extractNationalEmblemData(JSONObject baiduResponse) {
        OcrIdcardResponse.NationalEmblemData data = new OcrIdcardResponse.NationalEmblemData();

        if(baiduResponse.has(WORDS_RESULT_FIELD)) {
            JSONObject wordsResult = baiduResponse.getJSONObject(WORDS_RESULT_FIELD);

            // 提取各字段
            data.setIssue_authority(extractFieldValue(wordsResult, FIELD_ISSUE_AUTHORITY));
            data.setValid_date_start(formatValidDate(extractFieldValue(wordsResult, FIELD_ISSUE_DATE)));
            data.setValid_date_end(formatValidDate(extractFieldValue(wordsResult, FIELD_EXPIRE_DATE)));
        }

        return data;
    }

    /**
     * 从百度响应中提取字段值
     */
    private String extractFieldValue(JSONObject wordsResult, String fieldName) {
        if(wordsResult.has(fieldName)) {
            JSONObject fieldObj = wordsResult.getJSONObject(fieldName);
            return fieldObj.optString(WORDS_FIELD, "");
        }
        return "";
    }

    /**
     * 格式化出生日期，将"19920818"格式转换为"1992年8月18日"格式
     */
    private String formatBirthDate(String birthDate) {
        if(birthDate == null || birthDate.trim().isEmpty()) {
            return birthDate;
        }

        String cleanDate = birthDate.trim();
        if(cleanDate.matches("\\d{8}")) {
            String year = cleanDate.substring(0, 4);
            String month = cleanDate.substring(4, 6);
            String day = cleanDate.substring(6, 8);

            // 去掉月份和日期的前导零
            int monthInt = Integer.parseInt(month);
            int dayInt = Integer.parseInt(day);

            return year + "年" + monthInt + "月" + dayInt + "日";
        }

        return birthDate;
    }

    /**
     * 格式化有效日期，将"20100818"格式转换为"2010.08.18"格式
     */
    private String formatValidDate(String validDate) {
        if(validDate == null || validDate.trim().isEmpty()) {
            return validDate;
        }

        String cleanDate = validDate.trim();
        if(cleanDate.matches("\\d{8}")) {
            String year = cleanDate.substring(0, 4);
            String month = cleanDate.substring(4, 6);
            String day = cleanDate.substring(6, 8);

            return year + "." + month + "." + day;
        }

        return validDate;
    }

    /**
     * 构建错误响应
     */
    private OcrIdcardResponse buildErrorResponse(String errorCode, String errorMsg, JSONObject baiduResponse) {
        OcrIdcardResponse response = new OcrIdcardResponse();
        response.setRequest_id(baiduResponse == null ? generateRequestId() : String.valueOf(baiduResponse.getLong("log_id")));

        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .code(errorCode)
                .message(errorMsg)
                .type(BAIDU_OCR_ERROR_TYPE)
                .httpCode(400)
                .build();
        response.setError(error);

        return response;
    }

    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return System.currentTimeMillis() + "," + UUID.randomUUID();
    }
}
