package com.ke.bella.openapi.protocol.ocr.idcard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.KeOcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.provider.ke.KeOcrHelper;
import com.ke.bella.openapi.protocol.ocr.provider.ke.KeRequest;
import com.ke.bella.openapi.protocol.ocr.provider.ke.KeResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@Component("keIdcard")
public class KeAdaptor implements IdcardAdaptor<KeOcrProperty> {

    // Ke OCR 身份证字段常量
    private static final String KEY_NAME = "XING_MING";
    private static final String KEY_SEX = "XING_BIE";
    private static final String KEY_NATIONALITY = "MIN_ZU";
    private static final String KEY_BIRTH_DATE = "CHU_SHENG_RI_QI";
    private static final String KEY_ADDRESS = "ZHU_ZHI";
    private static final String KEY_IDCARD_NUMBER = "SHEN_FEN_ZHENG_HAO";
    private static final String KEY_ISSUE_AUTHORITY = "QIAN_FA_JI_GUAN";
    private static final String KEY_VALID_DATE_START = "YOU_XIAO_QI_QI_SHI_RI_QI";
    private static final String KEY_VALID_DATE_END = "YOU_XIAO_QI_JIE_ZHI_RI_QI";
    private static final String KEY_IMAGE_TYPE = "TU_PIAN_LEI_XING";

    // 图片类型值常量
    private static final String VALUE_NATIONAL_EMBLEM = "2";

    @Autowired
    private KeOcrHelper keOcrHelper;

    @Override
    public String getDescription() {
        return "Ke OCR身份证识别协议";
    }

    @Override
    public Class<KeOcrProperty> getPropertyClass() {
        return KeOcrProperty.class;
    }

    @Override
    public OcrIdcardResponse idcard(OcrRequest request, String url, KeOcrProperty property) {
        KeRequest keRequest = keOcrHelper.requestConvert(request);
        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(keRequest)))
                .build();
        clearLargeData(request, keRequest);
        KeResponse keResponse = HttpUtils.httpRequest(httpRequest, KeResponse.class);
        return responseConvert(keResponse, keRequest);

    }

    private OcrIdcardResponse responseConvert(KeResponse keResponse, KeRequest keRequest) {
        if(keOcrHelper.hasError(keResponse)) {
            return keOcrHelper.buildErrorResponse(keResponse);
        }

        String requestId = keResponse.getRequestId();

        // 从structuredResult中提取图片类型
        String imageType = keOcrHelper.findValueByKey(keResponse, KEY_IMAGE_TYPE);

        // 根据图片类型确定身份证面（默认为人像面）
        OcrIdcardResponse.IdCardSide side = VALUE_NATIONAL_EMBLEM.equals(imageType)
                ? OcrIdcardResponse.IdCardSide.NATIONAL_EMBLEM
                : OcrIdcardResponse.IdCardSide.PORTRAIT;

        // 根据不同面类型构建响应数据
        Object data = buildCardData(keResponse, side);

        return OcrIdcardResponse.builder()
                .request_id(requestId)
                .side(side)
                .data(data)
                .build();
    }

    /**
     * 根据身份证面类型构建对应的数据结构
     */
    private Object buildCardData(KeResponse keResponse, OcrIdcardResponse.IdCardSide side) {
        if(side == OcrIdcardResponse.IdCardSide.PORTRAIT) {
            // 构建人像面数据
            return OcrIdcardResponse.PortraitData.builder()
                    .name(keOcrHelper.findValueByKey(keResponse, KEY_NAME))
                    .sex(keOcrHelper.findValueByKey(keResponse, KEY_SEX))
                    .nationality(keOcrHelper.findValueByKey(keResponse, KEY_NATIONALITY))
                    .birth_date(keOcrHelper.findValueByKey(keResponse, KEY_BIRTH_DATE))
                    .address(keOcrHelper.findValueByKey(keResponse, KEY_ADDRESS))
                    .idcard_number(keOcrHelper.findValueByKey(keResponse, KEY_IDCARD_NUMBER))
                    .build();
        } else {
            // 构建国徽面数据
            return OcrIdcardResponse.NationalEmblemData.builder()
                    .issue_authority(keOcrHelper.findValueByKey(keResponse, KEY_ISSUE_AUTHORITY))
                    .valid_date_start(keOcrHelper.findValueByKey(keResponse, KEY_VALID_DATE_START))
                    .valid_date_end(keOcrHelper.findValueByKey(keResponse, KEY_VALID_DATE_END))
                    .build();
        }
    }

}
