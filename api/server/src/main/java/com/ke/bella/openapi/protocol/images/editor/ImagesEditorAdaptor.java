package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import org.springframework.web.multipart.MultipartFile;
import com.ke.bella.openapi.protocol.images.ImageDataType;

import java.io.IOException;
import java.util.Base64;

/**
 * 图片编辑适配器接口
 */
public interface ImagesEditorAdaptor<T extends ImagesEditorProperty> extends IProtocolAdaptor {

    /**
     * 编辑图片
     * @param request 请求参数
     * @param url 请求地址
     * @param property 属性配置
     * @return 响应结果
     */
	default ImagesResponse editImages(ImagesEditRequest request, String url, T property) {
		try {

			ImageDataType dataType = processImageData(request, property);

			return doEditImages(request, url, property, dataType);
		} catch (IOException e) {
			throw new BizParamCheckException("图片编辑请求处理失败: " + e.getMessage());
		}
	}

	/**
	 * 执行图片编辑请求
	 */
	ImagesResponse doEditImages(ImagesEditRequest request, String url, T property, ImageDataType dataType) throws IOException;

	/**
	 * 处理图片数据，确定使用的数据类型并补充请求信息
	 */
	default ImageDataType processImageData(ImagesEditRequest request, T property) throws IOException {

		if (property.isSupportBase64() && request.getImage_b64_json() != null && !request.getImage_b64_json().isEmpty()) {
			return ImageDataType.BASE64;
		}


		if (property.isSupportUrl() && request.getImage_url() != null && !request.getImage_url().isEmpty()) {
			return ImageDataType.URL;
		}


		MultipartFile imageFile = request.getImage();
		if (property.isSupportFile() && imageFile != null && !imageFile.isEmpty()) {
			return ImageDataType.FILE;
		} else if(property.isSupportBase64() && imageFile != null && !imageFile.isEmpty()) {
			byte[] imageBytes = imageFile.getBytes();
			String base64Image = Base64.getEncoder().encodeToString(imageBytes);
			String contentType = imageFile.getContentType();
			String imageFormat = "png";
			if (contentType != null && contentType.startsWith("image/")) {
				imageFormat = contentType.substring("image/".length());
			}

			request.setImage_b64_json(String.format("data:image/%s;base64,%s", imageFormat, base64Image));
			return ImageDataType.BASE64;
		}


		StringBuilder errorMessage = new StringBuilder("请求参数格式错误，请使用以下支持的图像上传方式");
		if (property.isSupportFile()) {
			errorMessage.append("文件上传 ");
		}
		if (property.isSupportUrl()) {
			errorMessage.append("URL链接 ");
		}
		if (property.isSupportBase64()) {
			errorMessage.append("Base64编码");
		}

        throw new BizParamCheckException(errorMessage.toString());
	}
}
