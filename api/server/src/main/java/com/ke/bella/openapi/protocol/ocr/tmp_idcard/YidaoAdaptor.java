package com.ke.bella.openapi.protocol.ocr.tmp_idcard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.YidaoOcrProperty;
import com.ke.bella.openapi.protocol.ocr.provider.yidao.YidaoOcrHelper;
import com.ke.bella.openapi.protocol.ocr.provider.yidao.YidaoRequest;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@Slf4j
@Component("yidaoTmpIdcard")
public class YidaoAdaptor implements TmpIdcardAdaptor<YidaoOcrProperty> {

    @Autowired
    private YidaoOcrHelper yidaoOcrHelper;

    @Override
    public String getDescription() {
        return "易道OCR临时身份证协议";
    }

    @Override
    public Class<YidaoOcrProperty> getPropertyClass() {
        return YidaoOcrProperty.class;
    }

    @Override
    public OcrTmpIdcardResponse tmpIdcard(OcrRequest request, String url, YidaoOcrProperty property) {
        YidaoRequest yidaoRequest = yidaoOcrHelper.requestConvert(request, property);
        Request httpRequest = yidaoOcrHelper.buildRequest(yidaoRequest, url);
        clearLargeData(request, yidaoRequest);
        YidaoResponse yidaoResponse = HttpUtils.httpRequest(httpRequest, YidaoResponse.class);
        return responseConvert(yidaoResponse);
    }

    private OcrTmpIdcardResponse responseConvert(YidaoResponse yidaoResponse) {
        if(yidaoOcrHelper.hasError(yidaoResponse) || yidaoResponse.getResult() == null) {
            return yidaoOcrHelper.buildErrorResponse(yidaoResponse, error -> OcrTmpIdcardResponse.builder().error(error).build());
        }
        YidaoResponse.ResultData result = yidaoResponse.getResult();
        String validDate = yidaoOcrHelper.getFieldWords(result.getValid());
        String[] dates = yidaoOcrHelper.parseValidDate(validDate);
        OcrTmpIdcardResponse.TmpIdcardData data = OcrTmpIdcardResponse.TmpIdcardData.builder()
                .name(yidaoOcrHelper.getFieldWords(result.getName()))
                .sex(yidaoOcrHelper.getFieldWords(result.getGender()))
                .nationality(yidaoOcrHelper.getFieldWords(result.getNationality()))
                .birth_date(yidaoOcrHelper.getFieldWords(result.getBirthdate()))
                .address(yidaoOcrHelper.getFieldWords(result.getAddress()))
                .idcard_number(yidaoOcrHelper.getFieldWords(result.getIdno()))
                .issue_authority(yidaoOcrHelper.getFieldWords(result.getIssued()))
                .valid_date_start(dates[0])
                .valid_date_end(dates[1])
                .build();

        return OcrTmpIdcardResponse.builder()
                .request_id(yidaoResponse.getRequestId())
                .data(data)
                .build();
    }
}
