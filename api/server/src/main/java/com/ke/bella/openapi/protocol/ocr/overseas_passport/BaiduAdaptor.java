package com.ke.bella.openapi.protocol.ocr.overseas_passport;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.BaiduOcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduOcrHelper;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

/**
 * 百度OCR海外护照识别适配器
 */
@Slf4j
@Component("baiduOverseasPassport")
public class BaiduAdaptor implements OverseasPassportAdaptor<BaiduOcrProperty> {

    private static final String FIELD_PASSPORT_TYPE = "护照类型";
    private static final String FIELD_PASSPORT_NO   = "护照号";
    private static final String FIELD_NAME          = "姓名拼音";
    private static final String FIELD_SEX           = "性别";
    private static final String FIELD_BIRTH_DATE    = "出生日期";
    private static final String FIELD_NATIONALITY   = "国籍";
    private static final String FIELD_NATIONALITY_CODE = "国家码";
    private static final String FIELD_VALID_DATE_END = "有效期";
    private static final String FIELD_MRZ_CODE1     = "MRZCode1";
    private static final String FIELD_MRZ_CODE2     = "MRZCode2";

    @Autowired
    private BaiduOcrHelper baiduOcrHelper;

    @Override
    public String getDescription() {
        return "百度OCR海外护照识别协议";
    }

    @Override
    public Class<BaiduOcrProperty> getPropertyClass() {
        return BaiduOcrProperty.class;
    }

    @Override
    public OcrOverseasPassportResponse overseasPassport(OcrRequest request, String url, BaiduOcrProperty property) {
        BaiduRequest baiduRequest = baiduOcrHelper.requestConvert(request, BaiduRequest.builder().build());
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(baiduOcrHelper.buildCommonFormBody(baiduRequest, null))
                .build();
        clearLargeData(request, baiduRequest);
        BaiduResponse baiduResponse = HttpUtils.httpRequest(httpRequest, BaiduResponse.class);
        return responseConvert(baiduResponse);
    }

    private OcrOverseasPassportResponse responseConvert(BaiduResponse baiduResponse) {
        if (baiduOcrHelper.hasError(baiduResponse)) {
            return baiduOcrHelper.buildErrorResponse(baiduResponse,
                    error -> OcrOverseasPassportResponse.builder().error(error).build());
        }

        OcrOverseasPassportResponse response = new OcrOverseasPassportResponse();
        response.setRequest_id(String.valueOf(baiduResponse.getLogId()));

        Map<String, List<BaiduResponse.WordItem>> wordsResult = baiduResponse.getWordsResult();
        if (wordsResult != null) {
            String mrz1 = getWord(wordsResult, FIELD_MRZ_CODE1);
            String mrz2 = getWord(wordsResult, FIELD_MRZ_CODE2);
            String mrz = buildMrz(mrz1, mrz2);

            OcrOverseasPassportResponse.OverseasPassportData data =
                    OcrOverseasPassportResponse.OverseasPassportData.builder()
                            .passport_type(getWord(wordsResult, FIELD_PASSPORT_TYPE))
                            .passport_no(getWord(wordsResult, FIELD_PASSPORT_NO))
                            .name(getWord(wordsResult, FIELD_NAME))
                            .sex(getWord(wordsResult, FIELD_SEX))
                            .birth_date(getWord(wordsResult, FIELD_BIRTH_DATE))
                            .nationality(getWord(wordsResult, FIELD_NATIONALITY))
                            .nationality_code(getWord(wordsResult, FIELD_NATIONALITY_CODE))
                            .valid_date_end(getWord(wordsResult, FIELD_VALID_DATE_END))
                            .mrz(mrz)
                            .build();
            response.setData(data);
        }

        return response;
    }

    private String getWord(Map<String, List<BaiduResponse.WordItem>> wordsResult, String fieldName) {
        List<BaiduResponse.WordItem> items = wordsResult.get(fieldName);
        if (items != null && !items.isEmpty()) {
            String word = items.get(0).getWord();
            return (word != null && !word.isEmpty()) ? word : null;
        }
        return null;
    }

    /**
     * 拼接 MRZ 码：MRZCode1 + 换行 + MRZCode2
     */
    private String buildMrz(String mrz1, String mrz2) {
        if (mrz1 == null && mrz2 == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (mrz1 != null) {
            sb.append(mrz1);
        }
        if (mrz2 != null) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(mrz2);
        }
        return sb.toString();
    }
}
