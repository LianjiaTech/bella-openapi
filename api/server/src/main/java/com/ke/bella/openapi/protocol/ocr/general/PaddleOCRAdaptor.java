package com.ke.bella.openapi.protocol.ocr.general;

import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.protocol.ocr.BaiduOcrProperty;
import com.ke.bella.openapi.protocol.ocr.ImageRetrievalService;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.utils.HttpUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * 百度智能云千帆 PaddleOCR-VL 适配器
 */
@Slf4j
@Component("paddleOCR")
public class PaddleOCRAdaptor implements GeneralAdaptor<BaiduOcrProperty> {

    @Autowired
    private ImageRetrievalService imageRetrievalService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getDescription() {
        return "百度智能云千帆 PaddleOCR-VL 协议";
    }

    @Override
    public Class<BaiduOcrProperty> getPropertyClass() {
        return BaiduOcrProperty.class;
    }

    @Override
    public OcrGeneralResponse general(OcrRequest request, String url, BaiduOcrProperty property) {
        // 1. 构建 PaddleOCR 请求
        PaddleOCRRequest paddleRequest = buildPaddleRequest(request);

        // 2. 构建 HTTP 请求（JSON 格式）
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(buildJsonBody(paddleRequest))
                .build();

        // 3. 清理大数据
        clearLargeData(request, paddleRequest);

        // 4. 发送请求
        PaddleOCRResponse paddleResponse = HttpUtils.httpRequest(httpRequest, PaddleOCRResponse.class);

        // 5. 转换响应
        return responseConvert(paddleResponse);
    }

    /**
     * 构建 PaddleOCR 请求
     * 只负责文件字段的获取和转换，所有参数平铺透传给渠道
     */
    private PaddleOCRRequest buildPaddleRequest(OcrRequest request) {
        Map<String, Object> extraBody = request.getExtra_body();

        // 处理文件输入
        if (StringUtils.hasText(request.getFileId())) {
            // 方式1: file_id → 转为 Base64，覆盖到 extraBody 中的 file 字段
            byte[] imageData = imageRetrievalService.getImageFromFileId(request.getFileId());
            String base64Data = Base64.getEncoder().encodeToString(imageData);

            // 创建新的 Map 避免修改原始 extraBody
            Map<String, Object> params = extraBody != null ? new java.util.HashMap<>(extraBody) : new java.util.HashMap<>();
            params.put("file", base64Data);
            extraBody = params;

        } else if (extraBody == null || !extraBody.containsKey("file")) {
            // 没有提供任何文件输入
            throw new IllegalArgumentException(
                "必须提供 file_id 或 file (在请求体中) 之一。" +
                "file 可以是 URL 或 Base64 编码字符串"
            );
        }

        // extra_body 中的所有字段（包括 file）直接平铺覆盖到 PaddleOCRRequest
        return PaddleOCRRequest.builder()
                .model(request.getModel())
                .extraParams(extraBody)
                .build();
    }

    /**
     * 构建 JSON 请求体
     */
    private RequestBody buildJsonBody(PaddleOCRRequest request) {
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(request);
            return RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonBytes);
        } catch (Exception e) {
            log.error("序列化 PaddleOCRRequest 为 JSON 失败", e);
            throw new RuntimeException("构建请求体失败", e);
        }
    }

    /**
     * 响应转换：保存完整的响应对象
     */
    private OcrGeneralResponse responseConvert(PaddleOCRResponse response) {
        // 检查 PaddleOCR 的错误格式（error 对象）
        if (response.getError() != null) {
            return buildErrorResponse(response);
        }

        // 直接保存完整的 result 对象，不做任何信息丢失的转换
        return OcrGeneralResponse.builder()
                .requestId(response.getId())
                .data(response.getResult())
                .build();
    }

    /**
     * 构建错误响应
     * PaddleOCR-VL 使用嵌套的 error 对象，不同于百度 OCR 的 errorCode/errorMsg
     */
    private OcrGeneralResponse buildErrorResponse(PaddleOCRResponse response) {
        // 将完整的 error 对象作为数据返回
        return OcrGeneralResponse.builder()
                .requestId(response.getId())
                .data(response.getError())
                .build();
    }
}
