package com.ke.bella.openapi.protocol.ocr.general;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ke.bella.openapi.protocol.ocr.BaiduOcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduOcrHelper;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 百度通用文字识别OCR适配器
 */
@Slf4j
@Component("baiduGeneral")
public class BaiduAdaptor implements GeneralAdaptor<BaiduOcrProperty> {

    @Autowired
    private BaiduOcrHelper baiduOcrHelper;

    @Override
    public String getDescription() {
        return "百度通用OCR协议";
    }

    @Override
    public Class<BaiduOcrProperty> getPropertyClass() {
        return BaiduOcrProperty.class;
    }

    @Override
    public OcrGeneralResponse general(OcrRequest request, String url, BaiduOcrProperty property) {
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
     * 构建表单数据，将BaiduRequest转换为FormBody
     */
    private RequestBody buildFormBody(BaiduRequest request) {
        return baiduOcrHelper.buildCommonFormBody(request, builder -> {
            // PDF相关参数
            if(StringUtils.hasText(request.getPdfFile())) {
                builder.add("pdf_file", request.getPdfFile());
            }
            if(StringUtils.hasText(request.getPdfFileNum())) {
                builder.add("pdf_file_num", request.getPdfFileNum());
            }

            // OFD相关参数
            if(StringUtils.hasText(request.getOfdFile())) {
                builder.add("ofd_file", request.getOfdFile());
            }
            if(StringUtils.hasText(request.getOfdFileNum())) {
                builder.add("ofd_file_num", request.getOfdFileNum());
            }

            // 可选参数（使用默认值）
            if(StringUtils.hasText(request.getLanguageType())) {
                builder.add("language_type", request.getLanguageType());
            }
            if(StringUtils.hasText(request.getDetectDirection())) {
                builder.add("detect_direction", request.getDetectDirection());
            }
            if(StringUtils.hasText(request.getDetectLanguage())) {
                builder.add("detect_language", request.getDetectLanguage());
            }
            if(StringUtils.hasText(request.getParagraph())) {
                builder.add("paragraph", request.getParagraph());
            }
            if(StringUtils.hasText(request.getProbability())) {
                builder.add("probability", request.getProbability());
            }
        });
    }

    /**
     * 响应转换：将百度API响应转换为统一的OcrGeneralResponse
     */
    private OcrGeneralResponse responseConvert(BaiduResponse baiduResponse) {
        // 检查百度API返回的错误
        if(baiduOcrHelper.hasError(baiduResponse)) {
            return buildErrorResponse(baiduResponse);
        }

        // 构建成功响应
        OcrGeneralResponse.OcrGeneralResponseBuilder builder = OcrGeneralResponse.builder();

        // 设置请求ID
        if(baiduResponse.getLogId() != null) {
            builder.requestId(String.valueOf(baiduResponse.getLogId()));
        }

        // 转换识别结果
        if(baiduResponse.getWordsResult() != null && !baiduResponse.getWordsResult().isEmpty()) {
            List<String> wordsList = baiduResponse.getWordsResult().stream()
                    .map(BaiduResponse.WordsResult::getWords)
                    .collect(Collectors.toList());

            OcrGeneralResponse.GeneralData generalData = OcrGeneralResponse.GeneralData.builder()
                    .words(wordsList)
                    .build();
            builder.data(generalData);
        }

        return builder.build();
    }

    /**
     * 构建错误响应
     */
    private OcrGeneralResponse buildErrorResponse(BaiduResponse baiduResponse) {
        return baiduOcrHelper.buildErrorResponse(baiduResponse,
                error -> OcrGeneralResponse.builder().error(error).build());
    }
}
