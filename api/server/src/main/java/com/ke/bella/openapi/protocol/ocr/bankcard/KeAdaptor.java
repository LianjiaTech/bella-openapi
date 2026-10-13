package com.ke.bella.openapi.protocol.ocr.bankcard;

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
@Component("keBankcard")
public class KeAdaptor implements BankcardAdaptor<KeOcrProperty> {
    private static final String KEY_CARD_NUMBER = "KA_HAO";
    private static final String KEY_BANK_NAME = "YIN_HANG_MING_CHENG";
    private static final String KEY_VALID_DATE = "YOU_XIAO_QI_JIE_ZHI_RI_QI";

    @Autowired
    private KeOcrHelper keOcrHelper;

    @Override
    public String getDescription() {
        return "Ke OCR银行卡识别协议";
    }

    @Override
    public Class<KeOcrProperty> getPropertyClass() {
        return KeOcrProperty.class;
    }

    @Override
    public OcrBankcardResponse bankcard(OcrRequest request, String url, KeOcrProperty property) {
        KeRequest keRequest = keOcrHelper.requestConvert(request);
        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(keRequest)))
                .build();
        clearLargeData(request, keRequest);
        KeResponse keResponse = HttpUtils.httpRequest(httpRequest, KeResponse.class);
        return responseConvert(keResponse, keRequest);
    }

    private OcrBankcardResponse responseConvert(KeResponse keResponse, KeRequest keRequest) {
        if(keOcrHelper.hasError(keResponse)) {
            return OcrBankcardResponse.builder()
                    .error(keOcrHelper.buildError(keResponse))
                    .build();
        }
        String requestId = keResponse.getRequestId();
        OcrBankcardResponse.BankcardData data = OcrBankcardResponse.BankcardData.builder()
                .card_number(keOcrHelper.findValueByKey(keResponse, KEY_CARD_NUMBER))
                .bank_name(keOcrHelper.findValueByKey(keResponse, KEY_BANK_NAME))
                .valid_date(keOcrHelper.findValueByKey(keResponse, KEY_VALID_DATE))
                .build();
        return OcrBankcardResponse.builder()
                .request_id(requestId)
                .data(data)
                .build();
    }
}
