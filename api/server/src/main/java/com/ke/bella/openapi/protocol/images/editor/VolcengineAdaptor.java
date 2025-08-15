package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.*;
	import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 火山方舟图片编辑适配器
 */
@Component("VolcengineImagesEditor")
public class VolcengineAdaptor implements ImagesEditorAdaptor<ImagesEditorProperty> {

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

		if (request.getImage_url() == null && request.getImage_b64_json() == null)
		{
			throw new RuntimeException("图像信息缺失，仅支持输入图片的 Base64 编码或可访问的 URL");
		}
		Map<String, Object> newRequestMap = new HashMap<>();
		newRequestMap.put("prompt", request.getPrompt());
		newRequestMap.put("model", property.getDeployName());
		newRequestMap.put("user", request.getUser());
		if (request.getImage_url() != null) newRequestMap.put("image", request.getImage_url());
		if (request.getImage_b64_json() != null) newRequestMap.put("image", request.getImage_b64_json());

		Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());
		requestBuilder.url(url)
			.post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
				JacksonUtils.serialize(newRequestMap)));
		Request httpRequest = requestBuilder.build();

		return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
	}
}
