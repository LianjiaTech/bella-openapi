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
import java.util.Collection;

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

		if (property.isSupportBase64() && request.getImage_b64_json() != null && request.getImage_b64_json().length > 0) {
			return ImageDataType.BASE64;
		}


		if (property.isSupportUrl() && request.getImage_url() != null && request.getImage_url().length > 0) {
			return ImageDataType.URL;
		}


		// 处理image字段，支持单张(MultipartFile)和多张(MultipartFile[])
		Object imageObj = request.getImage();
		if (imageObj != null) {
			if (imageObj instanceof MultipartFile) {
				// 单张图片
				MultipartFile singleFile = (MultipartFile) imageObj;
				if (!singleFile.isEmpty()) {
					if (property.isSupportFile()) {
						return ImageDataType.FILE;
					} else if (property.isSupportBase64()) {
						// 单张图片转base64
						byte[] imageBytes = singleFile.getBytes();
						String base64Image = Base64.getEncoder().encodeToString(imageBytes);
						String contentType = singleFile.getContentType();
						String imageFormat = "png";
						if (contentType != null && contentType.startsWith("image/")) {
							imageFormat = contentType.substring("image/".length());
						}
						String base64WithPrefix = String.format("data:image/%s;base64,%s", imageFormat, base64Image);
						request.setImage_b64_json(new String[]{base64WithPrefix});
						return ImageDataType.BASE64;
					}
				}
			} else if (imageObj instanceof Collection) {
				// 处理Collection类型（如LinkedList）
				Collection<?> imageCollection = (Collection<?>) imageObj;
				if (!imageCollection.isEmpty()) {
					// 检查第一个元素是否为MultipartFile类型
					Object firstObj = imageCollection.iterator().next();
					if (firstObj instanceof MultipartFile) {
						MultipartFile firstFile = (MultipartFile) firstObj;
						if (!firstFile.isEmpty()) {
							if (property.isSupportFile()) {
								return ImageDataType.FILE;
							} else if (property.isSupportBase64()) {
								// 将所有文件转换为 base64
								String[] base64Images = new String[imageCollection.size()];
								int index = 0;
								for (Object obj : imageCollection) {
									if (obj instanceof MultipartFile) {
										MultipartFile imageFile = (MultipartFile) obj;
										if (!imageFile.isEmpty()) {
											byte[] imageBytes = imageFile.getBytes();
											String base64Image = Base64.getEncoder().encodeToString(imageBytes);
											String contentType = imageFile.getContentType();
											String imageFormat = "png";
											if (contentType != null && contentType.startsWith("image/")) {
												imageFormat = contentType.substring("image/".length());
											}
											base64Images[index] = String.format("data:image/%s;base64,%s", imageFormat, base64Image);
										}
									}
									index++;
								}
								request.setImage_b64_json(base64Images);
								return ImageDataType.BASE64;
							}
						}
					}
				}
			} else if (imageObj.getClass().isArray()) {
				// 处理数组类型（包括StandardMultipartFile[]等实现类）
				Object[] objArray = (Object[]) imageObj;
				if (objArray.length > 0) {
					// 检查第一个元素是否为MultipartFile类型
					if (objArray[0] instanceof MultipartFile) {
						MultipartFile firstFile = (MultipartFile) objArray[0];
						if (!firstFile.isEmpty()) {
							if (property.isSupportFile()) {
								return ImageDataType.FILE;
							} else if (property.isSupportBase64()) {
								// 将所有文件转换为 base64
								String[] base64Images = new String[objArray.length];
								for (int i = 0; i < objArray.length; i++) {
									if (objArray[i] instanceof MultipartFile) {
										MultipartFile imageFile = (MultipartFile) objArray[i];
										if (!imageFile.isEmpty()) {
											byte[] imageBytes = imageFile.getBytes();
											String base64Image = Base64.getEncoder().encodeToString(imageBytes);
											String contentType = imageFile.getContentType();
											String imageFormat = "png";
											if (contentType != null && contentType.startsWith("image/")) {
												imageFormat = contentType.substring("image/".length());
											}
											base64Images[i] = String.format("data:image/%s;base64,%s", imageFormat, base64Image);
										}
									}
								}
								request.setImage_b64_json(base64Images);
								return ImageDataType.BASE64;
							}
						}
					}
				}
			}
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
