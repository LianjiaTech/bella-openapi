package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Component
public class ImagesEditRequestResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType().equals(ImagesEditRequest.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter,
								  ModelAndViewContainer mavContainer,
								  NativeWebRequest webRequest,
								  WebDataBinderFactory binderFactory) throws Exception {

		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

		if (!(request instanceof MultipartHttpServletRequest)) {
			throw new IllegalArgumentException("Request must be multipart/form-data");
		}

		MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
		ImagesEditRequest editRequest = new ImagesEditRequest();

		// 设置基本参数
		editRequest.setModel(multipartRequest.getParameter("model"));
		editRequest.setPrompt(multipartRequest.getParameter("prompt"));
		editRequest.setSize(multipartRequest.getParameter("size"));
		editRequest.setResponse_format(multipartRequest.getParameter("response_format"));
		editRequest.setUser(multipartRequest.getParameter("user"));

		// 处理数字参数
		String nParam = multipartRequest.getParameter("n");
		if (nParam != null && !nParam.trim().isEmpty()) {
			try {
				editRequest.setN(Integer.valueOf(nParam));
			} catch (NumberFormatException e) {
				// 忽略无效的数字格式
			}
		}

		// 直接在这里合并 image 和 image[] 参数
		List<MultipartFile> imageFiles = new ArrayList<>();
		imageFiles.addAll(multipartRequest.getFiles("image"));
		imageFiles.addAll(multipartRequest.getFiles("image[]"));

		// 验证 image 字段不能为空
		if (imageFiles.isEmpty() || imageFiles.stream().allMatch(MultipartFile::isEmpty)) {
			throw new IllegalArgumentException("image field cannot be empty");
		}

		// 过滤掉空文件
		MultipartFile[] validFiles = imageFiles.stream()
			.filter(file -> !file.isEmpty())
			.toArray(MultipartFile[]::new);

		editRequest.setImage(validFiles); // 保持Object类型，实际存储MultipartFile[]

		// 处理mask文件
		MultipartFile maskFile = multipartRequest.getFile("mask");
		if (maskFile != null && !maskFile.isEmpty()) {
			editRequest.setMask(maskFile);
		}

		// 处理其他参数
		handleOtherParameters(multipartRequest, editRequest);

		return editRequest;
	}

	private void handleOtherParameters(MultipartHttpServletRequest request, ImagesEditRequest editRequest) {
		// 处理image_url参数
		String[] imageUrls = request.getParameterValues("image_url");
		if (imageUrls != null && imageUrls.length > 0) {
			editRequest.setImage_url(imageUrls);
		}

		// 处理image_b64_json参数
		String[] imageB64s = request.getParameterValues("image_b64_json");
		if (imageB64s != null && imageB64s.length > 0) {
			editRequest.setImage_b64_json(imageB64s);
		}

		// 处理额外的参数到extra_body，只做基本类型转换
		Map<String, String[]> parameterMap = request.getParameterMap();
		Set<String> knownParams = new HashSet<>(Arrays.asList(
			"model", "prompt", "size", "response_format", "user", "n",
			"image_url", "image_b64_json"
		));

		for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
			String key = entry.getKey();
			if (!knownParams.contains(key)) {
				String[] values = entry.getValue();
				Object value;

				if (values.length == 1) {
					value = convertBasicType(values[0]);
				} else {
					// 多个值，每个都转换类型
					Object[] convertedArray = new Object[values.length];
					for (int i = 0; i < values.length; i++) {
						convertedArray[i] = convertBasicType(values[i]);
					}
					value = convertedArray;
				}

				editRequest.setExtraBodyField(key, value);
			}
		}
	}

	/**
	 * 简单的类型转换，只处理常见类型
	 */
	private Object convertBasicType(String value) {
		if (value == null) {
			return null;
		}

		String trimmed = value.trim();

		// 布尔类型
		if ("true".equalsIgnoreCase(trimmed)) {
			return true;
		}
		if ("false".equalsIgnoreCase(trimmed)) {
			return false;
		}

		// 整数类型
		if (trimmed.matches("-?\\d+")) {
			try {
				return Integer.parseInt(trimmed);
			} catch (NumberFormatException e) {
				try {
					return Long.parseLong(trimmed);
				} catch (NumberFormatException e2) {
					return value; // 解析失败返回原字符串
				}
			}
		}

		// 小数类型
		if (trimmed.matches("-?\\d+\\.\\d+")) {
			try {
				return Double.parseDouble(trimmed);
			} catch (NumberFormatException e) {
				return value; // 解析失败返回原字符串
			}
		}

		// 默认返回字符串
		return value;
	}
}

