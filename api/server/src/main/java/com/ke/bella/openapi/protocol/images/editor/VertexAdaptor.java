package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.completion.gemini.GeminiRequest;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiResponse;
import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.ByteBufferRequestBody;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Gemini 图片编辑适配器
 */
@Component("GeminiImagesEditor")
public class VertexAdaptor implements ImagesEditorAdaptor<ImagesEditorProperty> {

	@Override
	public String endpoint() {
		return "/v1/images/edits";
	}

	@Override
	public String getDescription() {
		return "Vertex AI (Gemini) 图片编辑协议";
	}

	@Override
	public Class<?> getPropertyClass() {
		return ImagesEditorProperty.class;
	}

	@Override
	public ImagesResponse doEditImages(ImagesEditRequest request, String url, ImagesEditorProperty property, ImageDataType dataType) throws IOException {
		// 构建 Vertex AI URL（添加 :generateContent）
		String vertexUrl = buildVertexUrl(url);

		// 使用 VertexEditorConverter 将 OpenAI 格式转换为 Gemini 格式
		GeminiRequest geminiRequest = VertexEditorConverter.convertToGeminiRequest(request, dataType);

		// 构建 HTTP 请求，确保异常时也能释放 ByteBuffer
		ByteBufferRequestBody requestBody = null;
		try {
			requestBody = buildHttpRequestBody(geminiRequest);
			Request httpRequest = buildHttpRequest(vertexUrl, requestBody, property);
			clearLargeData(request, geminiRequest);

			// 调用 Gemini API，获取 GeminiResponse
			GeminiResponse geminiResponse = HttpUtils.httpRequest(httpRequest, GeminiResponse.class);

			// 使用 VertexEditorConverter 转换为标准 OpenAI 格式
			// 从 inlineData 中提取图像，忽略 thoughtSignature
			return VertexEditorConverter.convertToImagesResponse(geminiResponse);
		} finally {
			// 确保 ByteBuffer 被释放，即使发生异常
			if (requestBody != null && !requestBody.isReleased()) {
				requestBody.release();
			}
		}
	}

	/**
	 * 构建 Vertex AI URL
	 * 添加 :generateContent 后缀
	 */
	private String buildVertexUrl(String baseUrl) {
		return baseUrl + ":generateContent";
	}

	/**
	 * 构建 ByteBufferRequestBody
	 * 使用 DirectByteBuffer 存储在堆外内存，减少 Young GC 和 Full GC
	 */
	private ByteBufferRequestBody buildHttpRequestBody(GeminiRequest geminiRequest) {
		// 从 EndpointContext 获取预先计算的序列化大小
		Integer serializedSize = com.ke.bella.openapi.EndpointContext.getSerializedSize();
		if (serializedSize == null) {
			// 如果没有预先计算，抛出异常（不应该发生）
			throw new IllegalStateException("Serialized size not found in EndpointContext");
		}

		return ByteBufferRequestBody.fromObject(
				MediaType.parse("application/json"),
				geminiRequest,
				serializedSize
		);
	}

	/**
	 * 构建 HTTP 请求
	 * 使用 ByteBuffer 优化内存占用，减少 GC 压力
	 * 特别适用于大数据量的图片和思维链请求
	 */
	private Request buildHttpRequest(String url, ByteBufferRequestBody body, ImagesEditorProperty property) {
		Request.Builder builder = authorizationRequestBuilder(property.getAuth())
				.url(url)
				.post(body)
				.header("Content-Type", "application/json");

		return builder.build();
	}

}
