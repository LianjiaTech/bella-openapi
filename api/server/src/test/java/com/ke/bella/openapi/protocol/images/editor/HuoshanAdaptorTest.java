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

@ExtendWith(MockitoExtension.class)
class HuoshanAdaptorTest {

	@InjectMocks
	private HuoshanAdaptor huoshanAdaptor;

	private ImagesEditRequest request;
	private ImagesEditorProperty property;
	private String testUrl;

	@BeforeEach
	void setUp() {
		// Initialize test data
		request = new ImagesEditRequest();
		request.setPrompt("test prompt");
		request.setUser("test_user");
		request.setResponse_format("url");
		request.setSize("1024x1024");
		request.setImage_b64_json(new String[]{"base64_image_data"});
		request.setImage_url(new String[]{"http://example.com/image.jpg"});

		property = new ImagesEditorProperty();
		property.setDeployName("test_model");

		// Setup authentication properties
		AuthorizationProperty authProperty = new AuthorizationProperty();
		authProperty.setApiKey("test_api_key");
		authProperty.setType(AuthType.BEARER); // Assume default is BEARER type
		property.setAuth(authProperty);

		testUrl = "http://test.com/v1/images/edits";
	}

	@Test
	void testEndpoint() {
		// Test endpoint method
		String endpoint = huoshanAdaptor.endpoint();
		assertEquals("/v1/images/edits", endpoint);
	}

	@Test
	void testGetDescription() {
		// Test getDescription method
		String description = huoshanAdaptor.getDescription();
		assertEquals("火山方舟图片编辑适配器", description);
	}

	@Test
	void testGetPropertyClass() {
		// Test getPropertyClass method
		Class<?> propertyClass = huoshanAdaptor.getPropertyClass();
		assertEquals(ImagesEditorProperty.class, propertyClass);
	}

	@Test
	void testBuildRequest_WithBase64DataType() {
		// Test building request with BASE64 data type
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenReturn("{\"test\":\"json\"}");

			Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.BASE64);

			assertNotNull(httpRequest);
			assertEquals(testUrl, httpRequest.url().toString());
			assertEquals("POST", httpRequest.method());
			assertNotNull(httpRequest.body());
			assertEquals("Bearer test_api_key", httpRequest.header("Authorization"));

			// Verify JacksonUtils.serialize was called
			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}

	@Test
	void testBuildRequest_WithUrlDataType() {
		// Test building request with URL data type
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenReturn("{\"test\":\"json\"}");

			Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.URL);

			assertNotNull(httpRequest);
			assertEquals(testUrl, httpRequest.url().toString());
			assertEquals("POST", httpRequest.method());
			assertNotNull(httpRequest.body());
			assertEquals("Bearer test_api_key", httpRequest.header("Authorization"));

			// Verify JacksonUtils.serialize was called
			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}

	@Test
	void testBuildRequest_WithFileDataType_ThrowsException() {
		// Test throwing exception when using FILE data type
		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
			huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.FILE);
		});

		assertEquals("火山方舟不支持直接文件上传", exception.getMessage());
	}

	@Test
	void testBuildRequest_WithNullOptionalFields() {
		// Test case with optional fields as null
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenReturn("{\"test\":\"json\"}");

			// Set optional fields to null
			request.setResponse_format(null);
			request.setSize(null);

			Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.BASE64);

			assertNotNull(httpRequest);
			assertEquals(testUrl, httpRequest.url().toString());
			assertEquals("POST", httpRequest.method());

			// Verify JacksonUtils.serialize was called
			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}

	@Test
	void testBuildRequest_VerifyRequestMapContent() {
		// Test correctness of request mapping content
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			// Capture Map passed to serialize method
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenAnswer(invocation -> {
					Map<String, Object> requestMap = invocation.getArgument(0);

					// Verify required fields
					assertEquals("test prompt", requestMap.get("prompt"));
					assertEquals("test_model", requestMap.get("model"));
					assertEquals("test_user", requestMap.get("user"));
					assertEquals(false, requestMap.get("watermark"));
					// Image field should be the first element of the array
					assertEquals("base64_image_data", requestMap.get("image"));

					// Verify optional fields
					assertEquals("url", requestMap.get("response_format"));
					assertEquals("1024x1024", requestMap.get("size"));

					return "{\"test\":\"json\"}";
				});

			huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.BASE64);

			// Verify JacksonUtils.serialize was called
			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}

	@Test
	void testBuildRequest_VerifyUrlDataType_UsesCorrectImageField() {
		// Test URL data type uses correct image field
		try (MockedStatic<JacksonUtils> jacksonUtilsMock = mockStatic(JacksonUtils.class)) {
			jacksonUtilsMock.when(() -> JacksonUtils.serialize(any(Map.class)))
				.thenAnswer(invocation -> {
					Map<String, Object> requestMap = invocation.getArgument(0);

					// Verify using URL field instead of BASE64 field
					assertEquals("http://example.com/image.jpg", requestMap.get("image"));

					return "{\"test\":\"json\"}";
				});

			huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.URL);

			jacksonUtilsMock.verify(() -> JacksonUtils.serialize(any(Map.class)), times(1));
		}
	}
}
