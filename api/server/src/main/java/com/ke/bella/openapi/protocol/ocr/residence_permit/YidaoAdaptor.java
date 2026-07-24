package com.ke.bella.openapi.protocol.ocr.residence_permit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.YidaoOcrProperty;
import com.ke.bella.openapi.protocol.ocr.provider.yidao.YidaoOcrHelper;
import com.ke.bella.openapi.protocol.ocr.provider.yidao.YidaoRequest;
import com.ke.bella.openapi.protocol.ocr.residencepermit.OcrResidencePermitResponse;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@Slf4j
@Component("yidaoResidencePermit")
public class YidaoAdaptor implements ResidencePermitAdaptor<YidaoOcrProperty> {

    @Autowired
    private YidaoOcrHelper yidaoOcrHelper;

    @Override
    public String getDescription() {
        return "易道OCR港澳台居民居住证识别协议";
    }

    @Override
    public Class<YidaoOcrProperty> getPropertyClass() {
        return YidaoOcrProperty.class;
    }

    @Override
    public OcrResidencePermitResponse hmtResidencePermit(OcrRequest request, String url, YidaoOcrProperty property) {
        YidaoRequest yidaoRequest = yidaoOcrHelper.requestConvert(request, property);
        Request httpRequest = yidaoOcrHelper.buildRequest(yidaoRequest, url);
        clearLargeData(request, yidaoRequest);
        YidaoResponse yidaoResponse = HttpUtils.httpRequest(httpRequest, YidaoResponse.class);
        return responseConvert(yidaoResponse);
    }

    private OcrResidencePermitResponse responseConvert(YidaoResponse yidaoResponse) {
        if(yidaoOcrHelper.hasError(yidaoResponse) || yidaoResponse.getResult() == null) {
            return yidaoOcrHelper.buildErrorResponse(yidaoResponse, error -> OcrResidencePermitResponse.builder().error(error).build());
        }
        YidaoResponse.ResultData result = yidaoResponse.getResult();
        OcrResidencePermitResponse.ResidencePermitSide side = determineSide(result);
        Object data = side == OcrResidencePermitResponse.ResidencePermitSide.PORTRAIT
                ? extractPortraitData(result)
                : extractNationalEmblemData(result);

        return OcrResidencePermitResponse.builder()
                .request_id(yidaoResponse.getRequestId())
                .side(side)
                .data(data)
                .build();
    }

    private OcrResidencePermitResponse.ResidencePermitSide determineSide(YidaoResponse.ResultData result) {
        if(result.getName() != null || result.getGender() != null || result.getBirthdate() != null) {
            return OcrResidencePermitResponse.ResidencePermitSide.PORTRAIT;
        }
        return OcrResidencePermitResponse.ResidencePermitSide.NATIONAL_EMBLEM;
    }

    private OcrResidencePermitResponse.PortraitData extractPortraitData(YidaoResponse.ResultData result) {
        return OcrResidencePermitResponse.PortraitData.builder()
                .name(yidaoOcrHelper.getFieldWords(result.getName()))
                .sex(yidaoOcrHelper.getFieldWords(result.getGender()))
                .birth_date(yidaoOcrHelper.getFieldWords(result.getBirthdate()))
                .address(yidaoOcrHelper.getFieldWords(result.getAddress()))
                .idcard_number(yidaoOcrHelper.getFieldWords(result.getIdno()))
                .build();
    }

    private OcrResidencePermitResponse.NationalEmblemData extractNationalEmblemData(YidaoResponse.ResultData result) {
        String validDate = yidaoOcrHelper.getFieldWords(result.getValid());
        String[] dates = yidaoOcrHelper.parseValidDate(validDate);

        return OcrResidencePermitResponse.NationalEmblemData.builder()
                .issue_authority(yidaoOcrHelper.getFieldWords(result.getIssued()))
                .valid_date_start(dates[0])
                .valid_date_end(dates[1])
                .issue_times(yidaoOcrHelper.getFieldWords(result.getIssuedTimes()))
                .eep_number(yidaoOcrHelper.getFieldWords(result.getPassNo()))
                .build();
    }
}
