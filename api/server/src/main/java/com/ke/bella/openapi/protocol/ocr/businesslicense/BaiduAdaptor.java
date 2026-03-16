package com.ke.bella.openapi.protocol.ocr.businesslicense;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.BaiduOcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduOcrHelper;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@Component("baiduBusinessLicense")
public class BaiduAdaptor implements BusinessLicenseAdaptor<BaiduOcrProperty> {

    private static final String FIELD_SOCIAL_CREDIT_CODE = "社会信用代码";
    private static final String FIELD_UNIT_NAME = "单位名称";
    private static final String FIELD_TYPE = "类型";
    private static final String FIELD_LEGAL_PERSON = "法人";
    private static final String FIELD_REGISTERED_CAPITAL = "注册资本";
    private static final String FIELD_PAID_IN_CAPITAL = "实收资本";
    private static final String FIELD_ESTABLISHMENT_DATE = "成立日期";
    private static final String FIELD_APPROVAL_DATE = "核准日期";
    private static final String FIELD_VALID_PERIOD = "有效期";
    private static final String FIELD_VALID_PERIOD_START = "有效期起始日期";
    private static final String FIELD_ADDRESS = "地址";
    private static final String FIELD_REGISTRATION_AUTHORITY = "登记机关";
    private static final String FIELD_CERTIFICATE_NUMBER = "证件编号";
    private static final String FIELD_TAX_REGISTRATION_NUMBER = "税务登记号";
    private static final String FIELD_BUSINESS_SCOPE = "经营范围";
    private static final String FIELD_COMPOSITION_FORM = "组成形式";

    @Autowired
    private BaiduOcrHelper baiduOcrHelper;

    @Override
    public String getDescription() {
        return "百度OCR营业执照识别协议";
    }

    @Override
    public Class<BaiduOcrProperty> getPropertyClass() {
        return BaiduOcrProperty.class;
    }

    @Override
    public OcrBusinessLicenseResponse businessLicense(OcrRequest request, String url, BaiduOcrProperty property) {
        BaiduRequest baiduRequest = baiduOcrHelper.requestConvert(request, BaiduRequest.builder().build());
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(buildFormBody(baiduRequest))
                .build();
        clearLargeData(request, baiduRequest);
        BaiduResponse baiduResponse = HttpUtils.httpRequest(httpRequest, BaiduResponse.class);
        return responseConvert(baiduResponse);
    }

    private RequestBody buildFormBody(BaiduRequest request) {
        return baiduOcrHelper.buildCommonFormBody(request, builder -> {
            builder.add("accuracy", request.getAccuracy())
                    .add("risk_warn", request.getRiskWarn())
                    .add("detect_quality", request.getDetectQuality())
                    .add("fullwidth_shift", request.getFullwidthShift());
        });
    }

    private OcrBusinessLicenseResponse responseConvert(BaiduResponse baiduResponse) {
        if(baiduOcrHelper.hasError(baiduResponse)) {
            return buildErrorResponse(baiduResponse);
        }

        OcrBusinessLicenseResponse response = OcrBusinessLicenseResponse.builder()
                .request_id(String.valueOf(baiduResponse.getLogId()))
                .data(extractBusinessLicenseData(baiduResponse))
                .build();

        return response;
    }

    private OcrBusinessLicenseResponse.BusinessLicenseData extractBusinessLicenseData(BaiduResponse baiduResponse) {
        if(baiduResponse.getWordsResult() == null) {
            return OcrBusinessLicenseResponse.BusinessLicenseData.builder().build();
        }

        Map<String, BaiduResponse.WordResult> wordsResult = baiduResponse.getWordsResult();

        String establishmentDate = getFieldValue(wordsResult, FIELD_ESTABLISHMENT_DATE);
        String approvalDate = getFieldValue(wordsResult, FIELD_APPROVAL_DATE);
        String validPeriodStart = getFieldValue(wordsResult, FIELD_VALID_PERIOD_START);
        String validPeriod = getFieldValue(wordsResult, FIELD_VALID_PERIOD);

        String[] businessTermDates = buildBusinessTermDates(validPeriodStart, validPeriod);

        return OcrBusinessLicenseResponse.BusinessLicenseData.builder()
                .unified_social_credit_code(getFieldValue(wordsResult, FIELD_SOCIAL_CREDIT_CODE))
                .name(getFieldValue(wordsResult, FIELD_UNIT_NAME))
                .entity_type(getFieldValue(wordsResult, FIELD_TYPE))
                .legal_representative(getFieldValue(wordsResult, FIELD_LEGAL_PERSON))
                .registered_capital(getFieldValue(wordsResult, FIELD_REGISTERED_CAPITAL))
                .paid_in_capital(getFieldValue(wordsResult, FIELD_PAID_IN_CAPITAL))
                .business_scope(getFieldValue(wordsResult, FIELD_BUSINESS_SCOPE))
                .establishment_date(establishmentDate)
                .business_term_start(businessTermDates[0])
                .business_term_end(businessTermDates[1])
                .address(getFieldValue(wordsResult, FIELD_ADDRESS))
                .issue_authority(getFieldValue(wordsResult, FIELD_REGISTRATION_AUTHORITY))
                .issue_date(approvalDate)
                .taxpayer_id(getFieldValue(wordsResult, FIELD_TAX_REGISTRATION_NUMBER))
                .license_number(getFieldValue(wordsResult, FIELD_CERTIFICATE_NUMBER))
                .composition_form(getFieldValue(wordsResult, FIELD_COMPOSITION_FORM))
                .build();
    }

    private String getFieldValue(Map<String, BaiduResponse.WordResult> wordsResult, String fieldName) {
        BaiduResponse.WordResult wordResult = wordsResult.get(fieldName);
        if(wordResult != null && wordResult.getWords() != null && !"无".equals(wordResult.getWords())) {
            return wordResult.getWords();
        }
        return null;
    }

    private String[] buildBusinessTermDates(String startDate, String endDate) {
        String formattedStart = isValidDate(startDate) ? startDate : null;
        String formattedEnd;
        if (endDate != null && endDate.contains("长期")) {
            formattedEnd = "长期";
        } else if (isValidDate(endDate)) {
            formattedEnd = endDate;
        } else {
            formattedEnd = null;
        }
        return new String[]{formattedStart, formattedEnd};
    }

    private boolean isValidDate(String date) {
        return date != null && date.matches("\\d{4}年\\d{2}月\\d{2}日");
    }

    private OcrBusinessLicenseResponse buildErrorResponse(BaiduResponse baiduResponse) {
        return baiduOcrHelper.buildErrorResponse(baiduResponse,
                error -> OcrBusinessLicenseResponse.builder().error(error).build());
    }
}
