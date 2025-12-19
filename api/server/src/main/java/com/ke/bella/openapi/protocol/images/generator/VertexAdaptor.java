package com.ke.bella.openapi.protocol.images.generator;

import com.ke.bella.openapi.protocol.completion.gemini.GeminiRequest;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiResponse;
import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

/**
 * Gemini文生图适配器
 */
@Component("GeminiImagesGenerator")
public class VertexAdaptor implements ImagesGeneratorAdaptor<ImagesProperty> {

	@Override
	public String endpoint() {
		return "/v1/images/generations";
	}

	@Override
	public String getDescription() {
		return "Vertex通信协议";
	}

	@Override
	public Class<?> getPropertyClass() {
		return ImagesProperty.class;
	}

	@Override
	public ImagesResponse generateImages(ImagesRequest request, String url, ImagesProperty property) {
		// 构建 Vertex AI URL（添加 :generateContent）
		String vertexUrl = buildVertexUrl(url);

		// 使用 VertexConverter 将 OpenAI 格式转换为 Gemini 格式
		GeminiRequest geminiRequest = VertexConverter.convertToGeminiRequest(request);

		// 构建 HTTP 请求
		Request httpRequest = buildHttpRequest(vertexUrl, geminiRequest, property);
		clearLargeData(request, geminiRequest);

		// 调用 Gemini API，获取 GeminiResponse
		GeminiResponse geminiResponse = HttpUtils.httpRequest(httpRequest, GeminiResponse.class);

		// 使用 VertexConverter 转换为标准 OpenAI 格式
		// 从 inlineData 中提取图像，忽略 thoughtSignature
		return VertexConverter.convertToImagesResponse(geminiResponse);
	}

	/**
	 * 构建 Vertex AI URL
	 * 添加 :generateContent 后缀
	 */
	private String buildVertexUrl(String baseUrl) {
		return baseUrl + ":generateContent";
	}

	/**
	 * 构建 HTTP 请求
	 * 参考 chat completion 的实现方式
	 */
	private Request buildHttpRequest(String url, GeminiRequest geminiRequest, ImagesProperty property) {
		byte[] requestBytes = JacksonUtils.toByte(geminiRequest);
		RequestBody body = RequestBody.create(MediaType.parse("application/json"), requestBytes);

		Request.Builder builder = authorizationRequestBuilder(property.getAuth())
				.url(url)
				.post(body)
				.header("Content-Type", "application/json");
 
		return builder.build();
	}

}
