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
        // 1. 构建 PaddleOCR 请求（内部包含 fileType 校验）
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
     *
     * 关键点：在映射 file 字段时进行 fileType 校验
     */
    private PaddleOCRRequest buildPaddleRequest(OcrRequest request) {
        PaddleOCRRequest.PaddleOCRRequestBuilder builder = PaddleOCRRequest.builder()
                .model(request.getModel());

        Map<String, Object> extraBody = request.getExtra_body();

        // 处理文件输入 - 统一映射到 file 字段
        // 规则：
        // 1. file_id → 转为 Base64，需要 fileType
        // 2. file (URL) → 直接使用
        // 3. file (Base64) → 需要 fileType

        if (StringUtils.hasText(request.getFileId())) {
            // 方式1: file_id → 转为 Base64
            if (extraBody == null || !extraBody.containsKey("fileType")) {
                throw new IllegalArgumentException(
                    "使用 file_id 时必须提供 fileType 参数。" +
                    "请在请求体中指定 fileType: 0 表示 PDF, 1 表示图片"
                );
            }

            // 通过 fileId 获取图片并转 Base64
            byte[] imageData = imageRetrievalService.getImageFromFileId(request.getFileId());
            String base64Data = Base64.getEncoder().encodeToString(imageData);
            builder.file(base64Data);

        } else if (extraBody != null && extraBody.containsKey("file")) {
            // 方式2/3: 从 extra_body 中获取 file 字段
            String file = String.valueOf(extraBody.get("file"));

            if (file.startsWith("https://") || file.startsWith("http://")) {
                // file 是 URL，直接使用
                builder.file(file);
            } else {
                // file 是 Base64，需要 fileType
                if (!extraBody.containsKey("fileType")) {
                    throw new IllegalArgumentException(
                        "当 file 为 Base64 编码时必须提供 fileType 参数。" +
                        "请在请求体中指定 fileType: 0 表示 PDF, 1 表示图片"
                    );
                }
                builder.file(file);
            }

        } else {
            // 没有提供任何文件输入
            throw new IllegalArgumentException(
                "必须提供 file_id 或 file (在请求体中) 之一。" +
                "file 可以是 URL 或 Base64 编码字符串"
            );
        }

        // 从 extra_body 中提取 PaddleOCR 参数并映射到字段
        if (extraBody != null) {
            extractField(extraBody, "fileType", Integer.class, builder::fileType);
            extractField(extraBody, "useDocOrientationClassify", Boolean.class, builder::useDocOrientationClassify);
            extractField(extraBody, "useDocUnwarping", Boolean.class, builder::useDocUnwarping);
            extractField(extraBody, "useLayoutDetection", Boolean.class, builder::useLayoutDetection);
            extractField(extraBody, "layoutNms", Boolean.class, builder::layoutNms);
            extractField(extraBody, "useChartRecognition", Boolean.class, builder::useChartRecognition);
            extractField(extraBody, "repetitionPenalty", Float.class, builder::repetitionPenalty);
            extractField(extraBody, "temperature", Float.class, builder::temperature);
            extractField(extraBody, "topP", Float.class, builder::topP);
            extractField(extraBody, "minPixels", Integer.class, builder::minPixels);
            extractField(extraBody, "maxPixels", Integer.class, builder::maxPixels);
            extractField(extraBody, "visualize", Boolean.class, builder::visualize);
        }

        return builder.build();
    }

    /**
     * 从 Map 中提取字段并调用 setter
     */
    @SuppressWarnings("unchecked")
    private <T> void extractField(Map<String, Object> map, String key, Class<T> type,
                                   java.util.function.Consumer<T> setter) {
        if (map.containsKey(key)) {
            Object value = map.get(key);
            if (type.isInstance(value)) {
                setter.accept((T) value);
            }
        }
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
