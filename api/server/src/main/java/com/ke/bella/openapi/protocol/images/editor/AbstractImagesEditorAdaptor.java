package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.Request;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * 图片编辑适配器抽象基类
 */
public abstract class AbstractImagesEditorAdaptor implements ImagesEditorAdaptor<ImagesEditorProperty> {

	@Override
	public Class<?> getPropertyClass() {
		return ImagesEditorProperty.class;
	}

	@Override
	public ImagesResponse editImages(ImagesEditRequest request, String url, ImagesEditorProperty property) {
		try {
			Request httpRequest = buildRequest(request, url, property);
			return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
		} catch (IOException e) {
			throw new BizParamCheckException("图片编辑请求处理失败: " + e.getMessage());
		}
	}

	/**
	 * 构建HTTP请求
	 */
	protected abstract Request buildRequest(ImagesEditRequest request, String url, ImagesEditorProperty property) throws IOException;

	/**
	 * 处理图片数据，返回适当的表示形式
	 */
	protected String processImageData(ImagesEditRequest request, ImagesEditorProperty property) throws IOException {
		MultipartFile imageFile = request.getImage();

		if (property.isSupportBase64() && request.getImage_b64_json() != null && !request.getImage_b64_json().isEmpty()) {
			return request.getImage_b64_json();
		} else if (property.isSupportUrl() && request.getImage_url() != null && !request.getImage_url().isEmpty()) {
			return request.getImage_url();
		} else if (property.isSupportBase64() && imageFile != null && !imageFile.isEmpty()) {
			byte[] imageBytes = imageFile.getBytes();
			String base64Image = Base64.getEncoder().encodeToString(imageBytes);
			String contentType = imageFile.getContentType();
			String imageFormat = "png";
			if (contentType != null && contentType.startsWith("image/")) {
				imageFormat = contentType.substring("image/".length());
			}
			return String.format("data:image/%s;base64,%s", imageFormat, base64Image);
		} else {
			StringBuilder supportedFormats = new StringBuilder("支持的格式 ");
			if (property.isSupportFile()) {
				supportedFormats.append("文件上传 ");
			}
			if (property.isSupportUrl()) {
				supportedFormats.append("URL ");
			}
			if (property.isSupportBase64()) {
				supportedFormats.append("Base64");
			}

			throw new BizParamCheckException(supportedFormats.toString());
		}
	}
}
