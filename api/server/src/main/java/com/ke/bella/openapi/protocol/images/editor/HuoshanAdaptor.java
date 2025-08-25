package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
	public ImagesResponse doEditImages(ImagesEditRequest request, String url, ImagesEditorProperty property, ImageDataType dataType) throws IOException {
		Request httpRequest = buildRequest(request, url, property, dataType);
		return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
	}

	/**
	 * 构建HTTP请求
	 */
	protected Request buildRequest(ImagesEditRequest request, String url, ImagesEditorProperty property, ImageDataType dataType) {
		Map<String, Object> requestMap = new HashMap<>();
		requestMap.put("prompt", request.getPrompt());
		requestMap.put("model", property.getDeployName());
		requestMap.put("user", request.getUser());
		requestMap.put("watermark", false);

		switch (dataType) {
			case BASE64:
				requestMap.put("image", request.getImage_b64_json());
				break;
			case URL:
				requestMap.put("image", request.getImage_url());
				break;
			case FILE:
				throw new IllegalStateException("火山方舟不支持直接文件上传");
		}

		if (request.getResponse_format() != null) {
			requestMap.put("response_format", request.getResponse_format());
		}
		if (request.getSize() != null) {
			requestMap.put("size", request.getSize());
		}
		Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());
		requestBuilder.url(url)
			.post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
				JacksonUtils.serialize(requestMap)));

		return requestBuilder.build();
	}
}
