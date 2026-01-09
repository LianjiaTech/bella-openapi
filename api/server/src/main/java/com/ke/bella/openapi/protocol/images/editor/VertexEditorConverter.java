package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.completion.gemini.Candidate;
import com.ke.bella.openapi.protocol.completion.gemini.Content;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiRequest;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiResponse;
import com.ke.bella.openapi.protocol.completion.gemini.GenerationConfig;
import com.ke.bella.openapi.protocol.completion.gemini.Part;
import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Gemini 图像编辑请求/响应转换器
 * 将 OpenAI 格式转换为 Gemini 格式，并将响应转换回 OpenAI 格式
 */
@Slf4j
public class VertexEditorConverter {

    /**
     * 将 OpenAI 图像编辑请求转换为 Gemini 请求格式
     * 支持多张输入图片，全部拼接到 parts 中
     *
     * @param editRequest OpenAI 格式的图像编辑请求
     * @param dataType    图像数据类型（FILE/URL/BASE64）
     * 
     * @return Gemini 格式的请求
     */
    public static GeminiRequest convertToGeminiRequest(ImagesEditRequest editRequest, ImageDataType dataType) throws IOException {
        List<Part> parts = new ArrayList<>();

        // 1. 添加所有输入图像（作为 inlineData）
        switch (dataType) {
        case FILE:
            MultipartFile[] imageFiles = editRequest.getImage();
            if(imageFiles != null) {
                for (MultipartFile imageFile : imageFiles) {
                    if(imageFile != null && !imageFile.isEmpty()) {
                        String base64Image = Base64.getEncoder().encodeToString(imageFile.getBytes());
                        String mimeType = imageFile.getContentType() != null ? imageFile.getContentType() : "image/png";

                        parts.add(Part.builder()
                                .inlineData(Part.InlineData.builder()
                                        .mimeType(mimeType)
                                        .data(base64Image)
                                        .build())
                                .build());
                    }
                }
            }
            break;

        case URL:
            String[] imageUrls = editRequest.getImage_url();
            if(imageUrls != null) {
                // Gemini 不直接支持 URL，需要先下载或使用 fileData
                log.warn("Gemini does not directly support image URLs for editing. Consider downloading the image first or using BASE64 format.");
                // 实际生产环境中可能需要下载 URL 图片转为 base64
            }
            break;

        case BASE64:
            String[] base64Images = editRequest.getImage_b64_json();
            if(base64Images != null) {
                for (String base64Data : base64Images) {
                    if(base64Data != null) {
                        // 处理 data URI 格式: data:image/png;base64,xxxxx
                        String mimeType = "image/png";
                        String cleanedData = base64Data;

                        if(base64Data.startsWith("data:")) {
                            int commaIndex = base64Data.indexOf(',');
                            if(commaIndex > 0) {
                                String header = base64Data.substring(0, commaIndex);
                                // 提取 MIME 类型
                                if(header.contains("image/")) {
                                    int typeStart = header.indexOf("image/");
                                    int typeEnd = header.indexOf(';', typeStart);
                                    if(typeEnd > typeStart) {
                                        mimeType = header.substring(typeStart, typeEnd);
                                    } else {
                                        // 没有分号，取到末尾
                                        mimeType = header.substring(typeStart);
                                    }
                                }
                                cleanedData = base64Data.substring(commaIndex + 1);
                            }
                        }

                        parts.add(Part.builder()
                                .inlineData(Part.InlineData.builder()
                                        .mimeType(mimeType)
                                        .data(cleanedData)
                                        .build())
                                .build());
                    }
                }
            }
            break;
        }

        // 2. 添加提示词（作为 text）
        if(editRequest.getPrompt() != null) {
            parts.add(Part.builder()
                    .text(editRequest.getPrompt())
                    .build());
        }

        // 3. 构建 Content（user 消息）
        Content userContent = Content.builder()
                .role("user")
                .parts(parts)
                .build();

        // 4. 构建 GenerationConfig
        // 注意：Gemini 的图像编辑数量由 prompt 决定，不通过 candidateCount 参数控制
        GenerationConfig.GenerationConfigBuilder configBuilder = GenerationConfig.builder();

        // 5. 构建 GeminiRequest
        return GeminiRequest.builder()
                .contents(Collections.singletonList(userContent))
                .generationConfig(configBuilder.build())
                .build()
                .offSafetySettings(); // 关闭安全过滤
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
            log.warn("Gemini did not generate any images for editing. Response text: {}", errorMessage);
        } else {
            log.info("Successfully extracted {} edited image(s) from Gemini response", imageDataList.size());
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
