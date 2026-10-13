package com.ke.bella.openapi.utils;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpUtilsTest {

	@Test
	void httpRequest_WhenOnlyInheritedErrorFieldIsPresent_DoesNotTreatResultAsEmpty() {
		Request request = new Request.Builder()
				.url("http://example.com/v1/images/edits")
				.get()
				.build();
		Interceptor interceptor = chain -> errorResponse(chain.request());

		ImagesResponse response = HttpUtils.httpRequest(request, ImagesResponse.class, (channelResponse, httpResponse) -> {
			channelResponse.getError().setHttpCode(httpResponse.code());
		}, interceptor);

		assertNotNull(response.getError());
		assertEquals(400, response.getError().getHttpCode());
		assertEquals("upstream validation failed", response.getError().getMessage());
	}

	@Test
	void httpRequest_WhenOpenapiResponseHasNoError_ThrowsChannelException() {
		Request request = new Request.Builder()
				.url("http://example.com/v1/queue/put")
				.get()
				.build();
		Interceptor interceptor = chain -> queueRateLimitResponse(chain.request());

		BellaException.ChannelException exception = assertThrows(BellaException.ChannelException.class,
				() -> HttpUtils.httpRequest(request, CompletionResponse.class,
						(channelResponse, httpResponse) -> {
						}, interceptor));

		assertEquals(429, exception.getHttpCode());
	}

	private Response errorResponse(Request request) {
		String body = "{\"error\":{\"message\":\"upstream validation failed\",\"type\":\"invalid_request_error\"}}";
		return new Response.Builder()
				.request(request)
				.protocol(Protocol.HTTP_1_1)
				.code(400)
				.message("")
				.body(ResponseBody.create(MediaType.parse("application/json"), body))
				.build();
	}

	private Response queueRateLimitResponse(Request request) {
		String body = "{\"code\":429,\"message\":\"Queue capacity exhausted\",\"timestamp\":1784690474531}";
		return new Response.Builder()
				.request(request)
				.protocol(Protocol.HTTP_1_1)
				.code(429)
				.message("")
				.body(ResponseBody.create(MediaType.parse("application/json"), body))
				.build();
	}
}
