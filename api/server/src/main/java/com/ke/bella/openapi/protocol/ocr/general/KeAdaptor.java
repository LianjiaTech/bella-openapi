package com.ke.bella.openapi.protocol.ocr.general;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

/**
 * KE OCR通用文字识别适配器
 */
@Slf4j
@Component("keGeneral")
public class KeAdaptor implements GeneralAdaptor<KeOcrProperty> {

    @Autowired
    private KeOcrHelper keOcrHelper;

    @Override
    public String getDescription() {
        return "KE OCR通用文字识别协议";
    }

    @Override
    public Class<KeOcrProperty> getPropertyClass() {
        return KeOcrProperty.class;
    }

    @Override
    public OcrGeneralResponse general(OcrRequest request, String url, KeOcrProperty property) {
        KeRequest keRequest = keOcrHelper.requestConvert(request);
        Request httpRequest = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(keRequest)))
                .build();
        clearLargeData(request, keRequest);
        KeResponse keResponse = HttpUtils.httpRequest(httpRequest, KeResponse.class);
        return responseConvert(keResponse, keRequest);
    }

    private OcrGeneralResponse responseConvert(KeResponse keResponse, KeRequest keRequest) {
        if(keOcrHelper.hasError(keResponse)) {
            return OcrGeneralResponse.builder()
                    .error(keOcrHelper.buildError(keResponse))
                    .build();
        }

        String requestId = keResponse.getRequestId();
        List<String> words = extractWords(keResponse);

        OcrGeneralResponse.GeneralData data = OcrGeneralResponse.GeneralData.builder()
                .words(words)
                .build();

        return OcrGeneralResponse.builder()
                .requestId(requestId)
                .data(data)
                .build();
    }

    private List<String> extractWords(KeResponse keResponse) {
        if(keResponse == null ||
                keResponse.getResult() == null ||
                keResponse.getResult().getWordsResult() == null) {
            return Collections.emptyList();
        }

        return keResponse.getResult().getWordsResult().stream()
                .map(KeResponse.WordsResult::getWords)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }
}
