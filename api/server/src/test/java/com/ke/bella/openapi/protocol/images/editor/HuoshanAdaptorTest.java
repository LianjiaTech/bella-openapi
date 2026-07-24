package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.AuthorizationProperty.AuthType;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
	void testBuildRequest_WithBase64DataType() throws IOException {
		// Test building request with BASE64 data type
		Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.BASE64);

		assertNotNull(httpRequest);
		assertEquals(testUrl, httpRequest.url().toString());
		assertEquals("POST", httpRequest.method());
		assertNotNull(httpRequest.body());
		assertEquals("Bearer test_api_key", httpRequest.header("Authorization"));
		assertEquals(Collections.singletonList("base64_image_data"), requestBody(httpRequest).get("image"));
	}

	@Test
	void testBuildRequest_WithUrlDataType() throws IOException {
		// Test building request with URL data type
		Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.URL);

		assertNotNull(httpRequest);
		assertEquals(testUrl, httpRequest.url().toString());
		assertEquals("POST", httpRequest.method());
		assertNotNull(httpRequest.body());
		assertEquals("Bearer test_api_key", httpRequest.header("Authorization"));
		assertEquals(Collections.singletonList("http://example.com/image.jpg"), requestBody(httpRequest).get("image"));
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
	void testBuildRequest_WithNullOptionalFields() throws IOException {
		// Test case with optional fields as null
		// Set optional fields to null
		request.setResponse_format(null);
		request.setSize(null);

		Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.BASE64);

		assertNotNull(httpRequest);
		assertEquals(testUrl, httpRequest.url().toString());
		assertEquals("POST", httpRequest.method());
		Map<String, Object> requestMap = requestBody(httpRequest);
		assertFalse(requestMap.containsKey("response_format"));
		assertFalse(requestMap.containsKey("size"));
	}

	@Test
	void testBuildRequest_VerifyRequestMapContent() throws IOException {
		// Test correctness of request mapping content
		Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.BASE64);
		Map<String, Object> requestMap = requestBody(httpRequest);

		// Verify required fields
		assertEquals("test prompt", requestMap.get("prompt"));
		assertEquals("test_model", requestMap.get("model"));
		assertEquals("test_user", requestMap.get("user"));
		assertEquals(false, requestMap.get("watermark"));
		assertEquals(Collections.singletonList("base64_image_data"), requestMap.get("image"));

		// Verify optional fields
		assertEquals("url", requestMap.get("response_format"));
		assertEquals("1024x1024", requestMap.get("size"));
	}

	@Test
	void testBuildRequest_VerifyUrlDataType_UsesCorrectImageField() throws IOException {
		// Test URL data type uses correct image field
		Request httpRequest = huoshanAdaptor.buildRequest(request, testUrl, property, ImageDataType.URL);
		Map<String, Object> requestMap = requestBody(httpRequest);

		// Verify using URL field instead of BASE64 field
		assertEquals(Collections.singletonList("http://example.com/image.jpg"), requestMap.get("image"));
	}

	private Map<String, Object> requestBody(Request httpRequest) throws IOException {
		Buffer buffer = new Buffer();
		httpRequest.body().writeTo(buffer);
		return JacksonUtils.deserialize(buffer.readUtf8(), new TypeReference<Map<String, Object>>() {
		});
	}
}
