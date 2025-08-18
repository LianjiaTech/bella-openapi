package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.*;
import org.springframework.stereotype.Component;

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
		if (property.isSupportUrl() && request.getImage_url() != null && !request.getImage_url().isEmpty()) {
            newRequestMap.put("image", request.getImage_url());
        } else if (property.isSupportBase64() && request.getImage_b64_json() != null && !request.getImage_b64_json().isEmpty()) {
            newRequestMap.put("image", request.getImage_b64_json());
        } else {
			throw new BizParamCheckException("支持的格式 "  + (property.isSupportUrl() ? "URL " : "") + (property.isSupportBase64() ? "Base64" : ""));
        }

		Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());
		requestBuilder.url(url)
			.post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
				JacksonUtils.serialize(newRequestMap)));
		Request httpRequest = requestBuilder.build();

		return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
	}
}
