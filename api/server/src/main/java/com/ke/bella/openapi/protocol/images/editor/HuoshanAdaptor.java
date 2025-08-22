package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import org.springframework.web.multipart.MultipartFile;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 火山方舟图片编辑适配器
 */
@Component("HuoshanImagesEditor")
public class HuoshanAdaptor implements ImagesEditorAdaptor<ImagesEditorProperty> {

	@Override
	public String endpoint() {
		return "/v1/images/edits";
	}

	@Override
	public String getDescription() {
		return "火山方舟图片编辑适配器";
	}

	@Override
	public Class<?> getPropertyClass() {
		return ImagesEditorProperty.class;
	}

	@Override
	public ImagesResponse editImages(ImagesEditRequest request, String url, ImagesEditorProperty property) {

		Map<String, Object> newRequestMap = new HashMap<>();
		newRequestMap.put("prompt", request.getPrompt());
		newRequestMap.put("model", property.getDeployName());
		newRequestMap.put("user", request.getUser());
		newRequestMap.put("watermark", false);
		MultipartFile imageFile = request.getImage();
		try {
			if (property.isSupportBase64() && request.getImage_b64_json() != null && !request.getImage_b64_json().isEmpty()) {
				newRequestMap.put("image", request.getImage_b64_json());
			} else if (property.isSupportBase64() && imageFile != null && !imageFile.isEmpty() && request.getImage_b64_json() == null) {
				byte[] imageBytes = imageFile.getBytes();
				String base64Image = Base64.getEncoder().encodeToString(imageBytes);
				String contentType = imageFile.getContentType();
				String imageFormat = "png";
				if (contentType != null) {
					if (contentType.startsWith("image/")) {
						imageFormat = contentType.substring("image/".length());
					}
				}
				String formattedBase64 = String.format("data:image/%s;base64,%s", imageFormat, base64Image);
				newRequestMap.put("image", formattedBase64);
			} else if (property.isSupportUrl() && request.getImage_url() != null && !request.getImage_url().isEmpty()) {
				newRequestMap.put("image", request.getImage_url());
			} else {
				throw new BizParamCheckException("支持的格式 "  + (property.isSupportUrl() ? "URL " : "") + (property.isSupportBase64() ? "Base64" : ""));
			}
		} catch (IOException e) {
			throw new BizParamCheckException("文件读取异常:" + e.getMessage());
		}

		if (request.getResponse_format() != null) {
			newRequestMap.put("response_format", request.getResponse_format());
		}
		if (request.getSize() != null) {
			newRequestMap.put("size", request.getSize());
		}
		Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());
		requestBuilder.url(url)
			.post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
				JacksonUtils.serialize(newRequestMap)));
		Request httpRequest = requestBuilder.build();

		return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
	}
}
