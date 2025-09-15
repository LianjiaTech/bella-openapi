package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.AuthorizationProperty.AuthType;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HuoshanAdaptor的单元测试
 * 测试覆盖：能力点、接口描述、类属性、请求构建、请求映射
 *
 * @author 张鑫宇
 * @since 2025-09-10
 */
@ExtendWith(MockitoExtension.class)
class HuoshanAdaptorTest {

	@InjectMocks
	private HuoshanAdaptor huoshanAdaptor;

	private ImagesEditRequest request;
	private ImagesEditorProperty property;
	private String testUrl;

	@BeforeEach
	void setUp() {
		// 初始化测试数据
		request = new ImagesEditRequest();
		request.setPrompt("test prompt");
		request.setUser("test_user");
		request.setResponse_format("url");
		request.setSize("1024x1024");
		request.setImage_b64_json("base64_image_data");
		request.setImage_url("http://example.com/image.jpg");

		property = new ImagesEditorProperty();
		property.setDeployName("test_model");

		// 设置认证属性
		AuthorizationProperty authProperty = new AuthorizationProperty();
		authProperty.setApiKey("test_api_key");
		authProperty.setType(AuthType.BEARER); // 假设默认是BEARER类型
		property.setAuth(authProperty);

		testUrl = "http://test.com/v1/images/edits";
	}

	@Test
	void testEndpoint() {
		// 测试endpoint方法
		String endpoint = huoshanAdaptor.endpoint();
		assertEquals("/v1/images/edits", endpoint);
	}

	@Test
	void testGetDescription() {
		// 测试getDescription方法
		String description = huoshanAdaptor.getDescription();
		assertEquals("火山方舟图片编辑适配器", description);
	}

	@Test
	void testGetPropertyClass() {
		// 测试getPropertyClass方法
		Class<?> propertyClass = huoshanAdaptor.getPropertyClass();
		assertEquals(ImagesEditorProperty.class, propertyClass);
	}

	@Test
	void testBuildRequest_WithBase64DataType() {
		// 测试使用BASE64数据类型构建请求
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenReturn("{\"test\":\"json\"}");

			Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.BASE64);

			assertNotNull(httpRequest);
			assertEquals(testUrl, httpRequest.url().toString());
			assertEquals("POST", httpRequest.method());
			assertNotNull(httpRequest.body());
			assertEquals("Bearer test_api_key", httpRequest.header("Authorization"));

			// 验证JacksonUtils.serialize被调用
			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}

	@Test
	void testBuildRequest_WithUrlDataType() {
		// 测试使用URL数据类型构建请求
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenReturn("{\"test\":\"json\"}");

			Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.URL);

			assertNotNull(httpRequest);
			assertEquals(testUrl, httpRequest.url().toString());
			assertEquals("POST", httpRequest.method());
			assertNotNull(httpRequest.body());
			assertEquals("Bearer test_api_key", httpRequest.header("Authorization"));

			// 验证JacksonUtils.serialize被调用
			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}

	@Test
	void testBuildRequest_WithFileDataType_ThrowsException() {
		// 测试使用FILE数据类型时抛出异常
		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
			huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.FILE);
		});

		assertEquals("火山方舟不支持直接文件上传", exception.getMessage());
	}

	@Test
	void testBuildRequest_WithNullOptionalFields() {
		// 测试可选字段为null的情况
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenReturn("{\"test\":\"json\"}");

			// 设置可选字段为null
			request.setResponse_format(null);
			request.setSize(null);

			Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.BASE64);

			assertNotNull(httpRequest);
			assertEquals(testUrl, httpRequest.url().toString());
			assertEquals("POST", httpRequest.method());

			// 验证JacksonUtils.serialize被调用
			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}

	@Test
	void testBuildRequest_VerifyRequestMapContent() {
		// 测试请求映射内容的正确性
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			// 捕获传递给serialize方法的Map
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenAnswer(invocation -> {
					Map<String, Object> requestMap = invocation.getArgument(0);

					// 验证必需字段
					assertEquals("test prompt", requestMap.get("prompt"));
					assertEquals("test_model", requestMap.get("model"));
					assertEquals("test_user", requestMap.get("user"));
					assertEquals(false, requestMap.get("watermark"));
					assertEquals("base64_image_data", requestMap.get("image"));

					// 验证可选字段
					assertEquals("url", requestMap.get("response_format"));
					assertEquals("1024x1024", requestMap.get("size"));

					return "{\"test\":\"json\"}";
				});

			huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.BASE64);

			// 验证JacksonUtils.serialize被调用
			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}

	@Test
	void testBuildRequest_VerifyUrlDataType_UsesCorrectImageField() {
		// 测试URL数据类型使用正确的图片字段
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenAnswer(invocation -> {
					Map<String, Object> requestMap = invocation.getArgument(0);

					// 验证使用URL字段而不是BASE64字段
					assertEquals("http://example.com/image.jpg", requestMap.get("image"));

					return "{\"test\":\"json\"}";
				});

			huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.URL);

			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}
}
