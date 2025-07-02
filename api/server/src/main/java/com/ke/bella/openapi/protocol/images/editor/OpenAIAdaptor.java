package com.ke.bella.openapi.protocol.images.editor;

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
    public ImagesResponse editImages(ImagesEditRequest request, String url, ImagesEditorProperty property) {
        try {
            // 构建multipart请求
            MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            // 添加图片数据（根据配置支持不同的输入方式）
            MultipartFile imageFile = request.getImage();
            if (property.isSupportFile() && imageFile != null && !imageFile.isEmpty()) {
                // 仅在配置支持时才允许文件上传
                multipartBuilder.addFormDataPart("image", imageFile.getOriginalFilename(),
                        RequestBody.create(MediaType.parse("image/png"), imageFile.getBytes()));
            } else if (property.isSupportUrl() && request.getImage_url() != null && !request.getImage_url().isEmpty()) {
                // 仅在配置支持时才允许URL输入
                multipartBuilder.addFormDataPart("image", request.getImage_url());
            } else if (property.isSupportBase64() && request.getImage_b64_json() != null && !request.getImage_b64_json().isEmpty()) {
                // 仅在配置支持时才允许Base64输入
                multipartBuilder.addFormDataPart("image", request.getImage_b64_json());
            } else {
                throw new RuntimeException("支持的格式 " + (property.isSupportFile() ? "文件上传 " : "") + (property.isSupportUrl() ? "URL " : "") + (property.isSupportBase64() ? "Base64" : ""));
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
            
            Request httpRequest = requestBuilder.build();
            return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
            
        } catch (IOException e) {
            throw new RuntimeException("图片编辑请求处理失败: " + e.getMessage(), e);
        }
    }
}
