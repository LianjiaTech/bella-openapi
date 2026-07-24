package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OpenAI图片编辑适配器
 */
@Component("OpenAIImagesEditor")
public class OpenAIAdaptor implements ImagesEditorAdaptor<ImagesEditorProperty> {

    private final Callbacks.ChannelErrorCallback<ImagesResponse> errorCallback = (errorResponse, res) -> {
        if(errorResponse.getError() != null) {
            errorResponse.getError().setHttpCode(res.code());
        }
    };

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
    public ImagesResponse doEditImages(ImagesEditRequest request, String url, ImagesEditorProperty property, ImageDataType dataType)
            throws IOException {
        Request httpRequest = buildRequest(request, url, property, dataType);
        clearLargeData(request);
        return HttpUtils.httpRequest(httpRequest, ImagesResponse.class, errorCallback);
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
            if(imageFiles != null) {
                List<MultipartFile> validImageFiles = Arrays.stream(imageFiles)
                        .filter(imageFile -> !imageFile.isEmpty())
                        .collect(Collectors.toList());
                String imageFieldName = validImageFiles.size() > 1 ? "image[]" : "image";
                for (MultipartFile imageFile : validImageFiles) {
                    multipartBuilder.addFormDataPart(imageFieldName, imageFile.getOriginalFilename(),
                            RequestBody.create(MediaType.parse("image/png"), imageFile.getBytes()));
                }
            }
            break;
        case URL:
            String[] imageUrls = request.getImage_url();
            if(imageUrls != null) {
                // 添加所有图片URL
                for (String imageUrl : imageUrls) {
                    multipartBuilder.addFormDataPart("image_url", imageUrl);
                }
            }
            break;
        case BASE64:
            String[] base64Images = request.getImage_b64_json();
            if(base64Images != null) {
                // 添加所有base64图片
                for (String base64Image : base64Images) {
                    multipartBuilder.addFormDataPart("image_b64_json", base64Image);
                }
            }
            break;
        }

        // 添加可选的遮罩文件
        MultipartFile maskFile = request.getMask();
        if(maskFile != null && !maskFile.isEmpty()) {
            multipartBuilder.addFormDataPart("mask", maskFile.getOriginalFilename(),
                    RequestBody.create(MediaType.parse("image/png"), maskFile.getBytes()));
        }

        // 添加必需的提示词
        if(request.getPrompt() != null) {
            multipartBuilder.addFormDataPart("prompt", request.getPrompt());
        }

        // 设置模型（property.getDeployName() 优先，否则使用 request.getModel()）
        String model = property.getDeployName();
        if(model == null || model.isEmpty()) {
            model = request.getModel();
        }
        multipartBuilder.addFormDataPart("model", model);

        // 添加可选参数
        if(request.getN() != null) {
            multipartBuilder.addFormDataPart("n", request.getN().toString());
        }

        if(request.getSize() != null) {
            multipartBuilder.addFormDataPart("size", request.getSize());
        }

        if(request.getResponse_format() != null) {
            multipartBuilder.addFormDataPart("response_format", request.getResponse_format());
        }

        if(request.getUser() != null) {
            multipartBuilder.addFormDataPart("user", request.getUser());
        }

        RequestBody requestBody = multipartBuilder.build();

        // 构建HTTP请求
        Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());
        requestBuilder.url(url).post(requestBody);

        return requestBuilder.build();
    }
}
