package com.ke.bella.openapi.protocol.ocr.hmt_travel_permit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.YidaoOcrProperty;
import com.ke.bella.openapi.protocol.ocr.hmttravelpermit.OcrHmtTravelPermitResponse;
import com.ke.bella.openapi.protocol.ocr.provider.yidao.YidaoOcrHelper;
import com.ke.bella.openapi.protocol.ocr.provider.yidao.YidaoRequest;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@Slf4j
@Component("yidaoHmtTravelPermit")
public class YidaoAdaptor implements HmtTravelPermitAdaptor<YidaoOcrProperty> {

    @Autowired
    private YidaoOcrHelper yidaoOcrHelper;

    @Override
    public String getDescription() {
        return "易道OCR港澳台居民往来内地通行证识别协议";
    }

    @Override
    public Class<YidaoOcrProperty> getPropertyClass() {
        return YidaoOcrProperty.class;
    }

    @Override
    public OcrHmtTravelPermitResponse hmtTravelPermit(OcrRequest request, String url, YidaoOcrProperty property) {
        YidaoRequest yidaoRequest = yidaoOcrHelper.requestConvert(request, property);
        Request httpRequest = yidaoOcrHelper.buildRequest(yidaoRequest, url);
        clearLargeData(request, yidaoRequest);
        YidaoResponse yidaoResponse = HttpUtils.httpRequest(httpRequest, YidaoResponse.class);
        return responseConvert(yidaoResponse);
    }

    private OcrHmtTravelPermitResponse responseConvert(YidaoResponse yidaoResponse) {
        if(yidaoOcrHelper.hasError(yidaoResponse) || yidaoResponse.getResult() == null) {
            return yidaoOcrHelper.buildErrorResponse(yidaoResponse, error -> OcrHmtTravelPermitResponse.builder().error(error).build());
        }
        YidaoResponse.ResultData result = yidaoResponse.getResult();
        boolean isBack = result.getName() != null && result.getMrzCode() != null;

        OcrHmtTravelPermitResponse.HmtTravelPermitData data;
        if (isBack) {
            data = OcrHmtTravelPermitResponse.HmtTravelPermitData.builder()
                    .idcard_name(yidaoOcrHelper.getFieldWords(result.getName()))
                    .idcard_number(yidaoOcrHelper.getFieldWords(result.getIdno2()))
                    .mrz(yidaoOcrHelper.getFieldWords(result.getMrzCode()))
                    .build();
        } else {
            String validDate = yidaoOcrHelper.getFieldWords(result.getValid());
            String[] dates = yidaoOcrHelper.parseValidDate(validDate);
            data = OcrHmtTravelPermitResponse.HmtTravelPermitData.builder()
                    .name(yidaoOcrHelper.getFieldWords(result.getChineseName()))
                    .name_en(yidaoOcrHelper.getFieldWords(result.getEnglishName()))
                    .birth_date(yidaoOcrHelper.getFieldWords(result.getBirthdate()))
                    .sex(yidaoOcrHelper.getFieldWords(result.getGender()))
                    .valid_date_start(dates[0])
                    .valid_date_end(dates[1])
                    .issue_authority(yidaoOcrHelper.getFieldWords(result.getAuthority()))
                    .permit_number(yidaoOcrHelper.getFieldWords(result.getIdno()))
                    .issue_times(yidaoOcrHelper.getFieldWords(result.getIssuedTimes()))
                    .build();
        }

        return OcrHmtTravelPermitResponse.builder()
                .request_id(yidaoResponse.getRequestId())
                .data(data)
                .build();
    }
}
