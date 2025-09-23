package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * OpenAI图片编辑适配器
 */
@Component("OpenAIImagesEditor")
public class OpenAIAdaptor implements ImagesEditorAdaptor<ImagesEditorProperty> {
    
    @Override
    public String endpoint() {
        return "/v1/images/edits";
    }
    
    @Override
    public String getDescription() {
        return "OpenAI图片编辑协议";
    }
    
    @Override
    public Class<?> getPropertyClass() {
        return ImagesEditorProperty.class;
    }
    
    @Override
    public ImagesResponse doEditImages(ImagesEditRequest request, String url, ImagesEditorProperty property, ImageDataType dataType) throws IOException {
        Request httpRequest = buildRequest(request, url, property, dataType);
        return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
    }

    /**
     * 构建HTTP请求
     */
    protected Request buildRequest(ImagesEditRequest request, String url, ImagesEditorProperty property, ImageDataType dataType) throws IOException {
        // 构建multipart请求
        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM);

        // 根据数据类型添加图片数据
        switch (dataType) {
            case FILE:
                MultipartFile[] imageFiles = request.getImage();
                if (imageFiles != null) {
                    for (MultipartFile imageFile : imageFiles) {
                        if (!imageFile.isEmpty()) {
                            multipartBuilder.addFormDataPart("image", imageFile.getOriginalFilename(),
                                RequestBody.create(MediaType.parse("image/png"), imageFile.getBytes()));
                        }
                    }
                }
                break;
            case URL:
                String[] imageUrls = request.getImage_url();
                if (imageUrls != null) {
                    // 添加所有图片URL
                    for (String imageUrl : imageUrls) {
                        multipartBuilder.addFormDataPart("image_url", imageUrl);
                    }
                }
                break;
            case BASE64:
                String[] base64Images = request.getImage_b64_json();
                if (base64Images != null) {
                    // 添加所有base64图片
                    for (String base64Image : base64Images) {
                        multipartBuilder.addFormDataPart("image_b64_json", base64Image);
                    }
                }
                break;
        }

        // 添加可选的遮罩文件
        MultipartFile maskFile = request.getMask();
        if (maskFile != null && !maskFile.isEmpty()) {
            multipartBuilder.addFormDataPart("mask", maskFile.getOriginalFilename(),
                RequestBody.create(MediaType.parse("image/png"), maskFile.getBytes()));
        }

        // 添加必需的提示词
        if (request.getPrompt() != null) {
            multipartBuilder.addFormDataPart("prompt", request.getPrompt());
        }

        // 设置模型（如果未指定则使用默认模型）
        String model = request.getModel();
        if (model == null || model.isEmpty()) {
            model = property.getDeployName();
        }
        multipartBuilder.addFormDataPart("model", model);

        // 添加可选参数
        if (request.getN() != null) {
            multipartBuilder.addFormDataPart("n", request.getN().toString());
        }

        if (request.getSize() != null) {
            multipartBuilder.addFormDataPart("size", request.getSize());
        }

        if (request.getResponse_format() != null) {
            multipartBuilder.addFormDataPart("response_format", request.getResponse_format());
        }

        if (request.getUser() != null) {
            multipartBuilder.addFormDataPart("user", request.getUser());
        }

        RequestBody requestBody = multipartBuilder.build();

        // 构建HTTP请求
        Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());
        requestBuilder.url(url).post(requestBody);

        return requestBuilder.build();
    }
}
