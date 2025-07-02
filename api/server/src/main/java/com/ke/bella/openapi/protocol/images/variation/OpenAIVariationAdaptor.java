package com.ke.bella.openapi.protocol.images.variation;

import com.ke.bella.openapi.protocol.images.ImagesVariationRequest;
import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * OpenAI图片变化适配器
 */
@Component("OpenAIImagesVariation")
public class OpenAIVariationAdaptor implements ImagesVariationAdaptor<ImagesProperty> {
    
    @Override
    public String endpoint() {
        return "/v1/images/variations";
    }
    
    @Override
    public String getDescription() {
        return "OpenAI图片变化协议";
    }
    
    @Override
    public Class<?> getPropertyClass() {
        return ImagesProperty.class;
    }
    
    @Override
    public ImagesResponse createVariations(ImagesVariationRequest request, String url, ImagesProperty property) {
        try {
            // 构建multipart请求
            MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);
            
            // 添加图片文件
            MultipartFile imageFile = request.getImage();
            if (imageFile != null && !imageFile.isEmpty()) {
                multipartBuilder.addFormDataPart("image", imageFile.getOriginalFilename(),
                        RequestBody.create(MediaType.parse("image/png"), imageFile.getBytes()));
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
            throw new RuntimeException("图片变化请求处理失败: " + e.getMessage(), e);
        }
    }
}
