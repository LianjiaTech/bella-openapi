package com.ke.bella.openapi.protocol.images.generator;

import com.ke.bella.openapi.protocol.completion.gemini.*;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gemini 图像生成请求/响应转换器
 * 将 OpenAI 格式转换为 Gemini 格式，并将响应转换回 OpenAI 格式
 */
@Slf4j
public class VertexConverter {

    /**
     * 将 OpenAI 图像请求转换为 Gemini 请求格式
     *
     * @param imagesRequest OpenAI 格式的图像生成请求
     * 
     * @return Gemini 格式的请求
     */
    public static GeminiRequest convertToGeminiRequest(ImagesRequest imagesRequest) {
        // 构建 Content：将 prompt 转换为 user 消息
        Part promptPart = Part.builder()
                .text(imagesRequest.getPrompt())
                .build();

        Content userContent = Content.builder()
                .role("user")
                .parts(Collections.singletonList(promptPart))
                .build();

        // 构建 GenerationConfig
        // 注意：Gemini 的图像生成数量由 prompt 决定，不通过 candidateCount 参数控制
        GenerationConfig.GenerationConfigBuilder configBuilder = GenerationConfig.builder();

        // 构建 GeminiRequest
        return GeminiRequest.builder()
                .contents(Collections.singletonList(userContent))
                .generationConfig(configBuilder.build())
                .build()
                .offSafetySettings(); // 关闭安全过滤（根据需要）
    }

    /**
     * 将 Gemini 响应转换为 OpenAI Images 格式
     * 从 candidates 中的 parts 提取 inlineData (base64 图像)
     * 忽略 thoughtSignature，专注于图像数据
     *
     * @param geminiResponse Gemini API 响应
     * 
     * @return OpenAI 格式的图像响应
     */
    public static ImagesResponse convertToImagesResponse(GeminiResponse geminiResponse) {
        if(geminiResponse == null || CollectionUtils.isEmpty(geminiResponse.getCandidates())) {
            log.warn("Gemini response is empty or has no candidates");
            return ImagesResponse.builder()
                    .created(DateTimeUtils.getCurrentSeconds())
                    .data(new ArrayList<>())
                    .build();
        }

        List<ImagesResponse.ImageData> imageDataList = new ArrayList<>();
        StringBuilder textContent = new StringBuilder(); // 收集文本内容用于错误提示

        // 遍历所有 candidates
        for (Candidate candidate : geminiResponse.getCandidates()) {
            if(candidate.getContent() == null || CollectionUtils.isEmpty(candidate.getContent().getParts())) {
                continue;
            }

            // 从 parts 中提取所有 inlineData (图像) 和文本
            for (Part part : candidate.getContent().getParts()) {
                // 提取图像数据
                if(part.getInlineData() != null) {
                    String base64Data = part.getInlineData().getData();
                    String mimeType = part.getInlineData().getMimeType();

                    if(base64Data != null) {
                        ImagesResponse.ImageData imageData = ImagesResponse.ImageData.builder()
                                .b64_json(base64Data)
                                .output_format(extractFormatFromMimeType(mimeType))
                                .build();
                        imageDataList.add(imageData);
                    }
                }

                // 收集文本内容（可能包含错误信息或说明）
                if(part.getText() != null) {
                    textContent.append(part.getText()).append(" ");
                }
            }
        }

        // 如果没有生成任何图像，记录警告并附带文本信息
        if(imageDataList.isEmpty()) {
            String errorMessage = textContent.length() > 0
                    ? textContent.toString().trim()
                    : "No images generated";
            log.warn("Gemini did not generate any images. Response text: {}", errorMessage);
        } else {
            log.info("Successfully extracted {} image(s) from Gemini response", imageDataList.size());
        }

        // 构建响应
        return ImagesResponse.builder()
                .created(DateTimeUtils.getCurrentSeconds())
                .data(imageDataList)
                .usage(convertUsage(geminiResponse.getUsageMetadata()))
                .build();
    }

    /**
     * 从 MIME 类型提取图像格式
     * 例如：image/png -> png, image/jpeg -> jpeg
     */
    private static String extractFormatFromMimeType(String mimeType) {
        if(mimeType == null) {
            return "png"; // 默认格式
        }

        if(mimeType.contains("/")) {
            String[] parts = mimeType.split("/");
            if(parts.length == 2) {
                return parts[1].toLowerCase();
            }
        }

        return "png";
    }

    /**
     * 转换 usage 信息
     */
    private static ImagesResponse.Usage convertUsage(com.ke.bella.openapi.protocol.completion.gemini.UsageMetadata usageMetadata) {
        if(usageMetadata == null) {
            return null;
        }

        return ImagesResponse.Usage.builder()
                .input_tokens(usageMetadata.getPromptTokenCount() != null ? usageMetadata.getPromptTokenCount() : 0)
                .output_tokens(usageMetadata.getCandidatesTokenCount() != null ? usageMetadata.getCandidatesTokenCount() : 0)
                .total_tokens(usageMetadata.getTotalTokenCount() != null ? usageMetadata.getTotalTokenCount() : 0)
                .build();
    }
}
