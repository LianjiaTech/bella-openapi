package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.AuthorizationProperty.AuthType;
import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.sun.net.httpserver.HttpServer;
import okhttp3.MultipartBody;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIAdaptorTest {

	private OpenAIAdaptor adaptor;
	private ImagesEditRequest request;
	private ImagesEditorProperty property;

	@BeforeEach
	void setUp() {
		adaptor = new OpenAIAdaptor();
		request = new ImagesEditRequest();
		request.setPrompt("edit prompt");
		request.setModel("gpt-image-1");

		AuthorizationProperty auth = new AuthorizationProperty();
		auth.setType(AuthType.BEARER);
		auth.setApiKey("test-key");

		property = new ImagesEditorProperty();
		property.setAuth(auth);
		property.setDeployName("gpt-image-1");
	}

	@Test
	void buildRequest_WithSingleImageFile_UsesImageField() throws IOException {
		request.setImage(new MockMultipartFile[] {
				new MockMultipartFile("image", "image-1.png", "image/png", "image-1".getBytes(StandardCharsets.UTF_8))
		});

		Request httpRequest = adaptor.buildRequest(request, "http://example.com/v1/images/edits", property, ImageDataType.FILE);
		MultipartBody body = (MultipartBody) httpRequest.body();

		assertNotNull(body);
		assertTrue(body.part(0).headers().get("Content-Disposition").contains("name=\"image\""));
	}

	@Test
	void buildRequest_WithMultipleImageFiles_UsesImageArrayField() throws IOException {
		request.setImage(new MockMultipartFile[] {
				new MockMultipartFile("image[]", "image-1.png", "image/png", "image-1".getBytes(StandardCharsets.UTF_8)),
				new MockMultipartFile("image[]", "image-2.png", "image/png", "image-2".getBytes(StandardCharsets.UTF_8))
		});

		Request httpRequest = adaptor.buildRequest(request, "http://example.com/v1/images/edits", property, ImageDataType.FILE);
		MultipartBody body = (MultipartBody) httpRequest.body();

		assertNotNull(body);
		assertTrue(body.part(0).headers().get("Content-Disposition").contains("name=\"image[]\""));
		assertTrue(body.part(1).headers().get("Content-Disposition").contains("name=\"image[]\""));
	}

	@Test
	void doEditImages_WithOpenAIErrorResponse_PreservesErrorMessage() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		byte[] responseBody = "{\"error\":{\"message\":\"Duplicate parameter: 'image'\",\"type\":\"invalid_request_error\",\"param\":\"image\",\"code\":\"invalid_request_error\"}}"
				.getBytes(StandardCharsets.UTF_8);
		server.createContext("/v1/images/edits", exchange -> {
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(400, responseBody.length);
			exchange.getResponseBody().write(responseBody);
			exchange.close();
		});
		server.start();
		try {
			request.setImage_url(new String[] { "https://example.com/image.png" });
			String url = "http://localhost:" + server.getAddress().getPort() + "/v1/images/edits";

			ImagesResponse response = adaptor.doEditImages(request, url, property, ImageDataType.URL);

			assertNotNull(response.getError());
			assertEquals(400, response.getError().getHttpCode());
			assertEquals("Duplicate parameter: 'image'", response.getError().getMessage());
			assertEquals("invalid_request_error", response.getError().getType());
			assertEquals("image", response.getError().getParam());
		} finally {
			server.stop(0);
		}
	}
}
