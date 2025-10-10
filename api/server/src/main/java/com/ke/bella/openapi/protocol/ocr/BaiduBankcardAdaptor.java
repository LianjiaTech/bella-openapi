package com.ke.bella.openapi.protocol.ocr;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.ocr.bankcard.BaiduBankcardRequest;
import com.ke.bella.openapi.protocol.ocr.bankcard.BaiduBankcardResponse;
import com.ke.bella.openapi.protocol.ocr.bankcard.OcrBankcardRequest;
import com.ke.bella.openapi.protocol.ocr.bankcard.OcrBankcardResponse;
import com.ke.bella.openapi.protocol.ocr.util.ImageConverter;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 百度银行卡OCR适配器
 */
@Slf4j
@Component("baiduBankcard")
public class BaiduBankcardAdaptor implements OcrBankcardAdaptor<BaiduOcrProperty> {

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
        return "百度OCR银行卡识别协议";
    }

    @Override
    public Class<BaiduOcrProperty> getPropertyClass() {
        return BaiduOcrProperty.class;
    }

    @Override
    public OcrBankcardResponse bankcard(OcrBankcardRequest request, String url, BaiduOcrProperty property) {
        BaiduBankcardRequest baiduBankcardRequest = requestConvert(request);
        Request httpRequest = buildRequest(baiduBankcardRequest, url, property);
        BaiduBankcardResponse baiduBankcardResponse = HttpUtils.httpRequest(httpRequest, BaiduBankcardResponse.class);
        return responseConvert(baiduBankcardResponse);
    }

    private BaiduBankcardRequest requestConvert(OcrBankcardRequest request) {
        BaiduBankcardRequest.BaiduBankcardRequestBuilder builder = BaiduBankcardRequest.builder();

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
    private Request buildRequest(BaiduBankcardRequest request, String url, BaiduOcrProperty property) {
        RequestBody formBody = buildFormBody(request);
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(formBody);
        return builder.build();
    }

    /**
     * 构建表单数据，将BaiduBankcardRequest转换为FormBody
     */
    private RequestBody buildFormBody(BaiduBankcardRequest request) {
        FormBody.Builder builder = new FormBody.Builder();

        // 图片参数二选一
        if(StringUtils.hasText(request.getUrl())) {
            builder.add("url", request.getUrl());
        } else if(StringUtils.hasText(request.getImage())) {
            builder.add("image", request.getImage());
        }

        // 可选参数，直接添加（字段已有默认值false）
        builder.add("detect_quality", request.getDetectQuality())
                .add("location", request.getLocation());

        return builder.build();
    }

    /**
     * 转换百度响应为统一格式
     */
    private OcrBankcardResponse responseConvert(BaiduBankcardResponse baiduBankcardResponse) {
        // 检查百度API返回的错误
        if(hasError(baiduBankcardResponse)) {
            return buildErrorResponse(baiduBankcardResponse);
        }

        // 构建成功响应
        OcrBankcardResponse response = new OcrBankcardResponse();
        response.setRequest_id(String.valueOf(baiduBankcardResponse.getLogId()));

        // 提取银行卡数据
        if(baiduBankcardResponse.getResult() != null) {
            OcrBankcardResponse.BankcardData data = extractBankcardData(baiduBankcardResponse.getResult());
            response.setData(data);
        }

        return response;
    }

    /**
     * 提取银行卡数据
     */
    private OcrBankcardResponse.BankcardData extractBankcardData(BaiduBankcardResponse.BankcardResult result) {
        return OcrBankcardResponse.BankcardData.builder()
                .card_number(result.getBankCardNumber())
                .bank_name(result.getBankName())
                .card_type(convertCardType(result.getBankCardType()))
                .valid_date(result.getValidDate())
                .build();
    }

    /**
     * 转换银行卡类型
     */
    private String convertCardType(Integer bankCardType) {
        if(bankCardType == null) {
            return "未知";
        }

        switch (bankCardType) {
        case 0:
            return "不能识别";
        case 1:
            return "借记卡";
        case 2:
            return "贷记卡";
        case 3:
            return "准贷记卡";
        case 4:
            return "预付费卡";
        default:
            return "未知";
        }
    }

    /**
     * 检查百度API是否返回错误
     */
    private boolean hasError(BaiduBankcardResponse baiduResponse) {
        return StringUtils.hasText(baiduResponse.getErrorCode()) &&
                !ERROR_CODE_SUCCESS.equals(baiduResponse.getErrorCode());
    }

    /**
     * 构建错误响应
     */
    private OcrBankcardResponse buildErrorResponse(BaiduBankcardResponse baiduResponse) {
        int errorCode = Integer.parseInt(baiduResponse.getErrorCode());
        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .code(baiduResponse.getErrorCode())
                .message(baiduResponse.getErrorMsg())
                .type(BAIDU_OCR_ERROR_TYPE)
                .httpCode(errorCode >= ERROR_CODE_THRESHOLD ? HttpStatus.BAD_REQUEST.value() : HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
        return OcrBankcardResponse.builder().error(error).build();
    }
}
