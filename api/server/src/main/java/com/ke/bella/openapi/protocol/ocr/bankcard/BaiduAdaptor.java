package com.ke.bella.openapi.protocol.ocr.bankcard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.BaiduOcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduOcrHelper;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 百度银行卡OCR适配器
 */
@Slf4j
@Component("baiduBankcard")
public class BaiduAdaptor implements BankcardAdaptor<BaiduOcrProperty> {

    @Autowired
    private BaiduOcrHelper baiduOcrHelper;

    @Override
    public String getDescription() {
        return "百度OCR银行卡识别协议";
    }

    @Override
    public Class<BaiduOcrProperty> getPropertyClass() {
        return BaiduOcrProperty.class;
    }

    @Override
    public OcrBankcardResponse bankcard(OcrRequest request, String url, BaiduOcrProperty property) {
        BaiduRequest baiduRequest = baiduOcrHelper.requestConvert(request, BaiduRequest.builder().build());
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(buildFormBody(baiduRequest))
                .build();
        clearLargeData(request, baiduRequest);
        BaiduResponse baiduResponse = HttpUtils.httpRequest(httpRequest, BaiduResponse.class);
        return responseConvert(baiduResponse);
    }

    /**
     * 构建表单数据，将BaiduBankcardRequest转换为FormBody
     */
    private RequestBody buildFormBody(BaiduRequest request) {
        return baiduOcrHelper.buildCommonFormBody(request, builder -> {
            builder.add("detect_quality", request.getDetectQuality())
                    .add("location", request.getLocation());
        });
    }

    /**
     * 转换百度响应为统一格式
     */
    private OcrBankcardResponse responseConvert(BaiduResponse baiduResponse) {
        // 检查百度API返回的错误
        if(baiduOcrHelper.hasError(baiduResponse)) {
            return buildErrorResponse(baiduResponse);
        }

        // 构建成功响应
        OcrBankcardResponse response = new OcrBankcardResponse();
        response.setRequest_id(String.valueOf(baiduResponse.getLogId()));

        // 提取银行卡数据
        if(baiduResponse.getResult() != null) {
            OcrBankcardResponse.BankcardData data = extractBankcardData(baiduResponse.getResult());
            response.setData(data);
        }

        return response;
    }

    /**
     * 提取银行卡数据
     */
    private OcrBankcardResponse.BankcardData extractBankcardData(BaiduResponse.BankcardResult result) {
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
     * 构建错误响应
     */
    private OcrBankcardResponse buildErrorResponse(BaiduResponse baiduResponse) {
        return baiduOcrHelper.buildErrorResponse(baiduResponse,
                error -> OcrBankcardResponse.builder().error(error).build());
    }
}
