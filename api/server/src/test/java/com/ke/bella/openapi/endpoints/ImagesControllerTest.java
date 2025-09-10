package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.images.generator.ImagesGeneratorAdaptor;
import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ImagesControllerTest {

	@Mock
	private ChannelRouter router;

	@Mock
	private AdaptorManager adaptorManager;

	@Mock
	private LimiterManager limiterManager;

	@Mock
	private ImagesGeneratorAdaptor<ImagesProperty> mockAdaptor;

	@Mock
	private ContentCachingRequestWrapper mockRequest;

	@InjectMocks
	private ImagesController imagesController;

	private EndpointProcessData mockProcessData;

	@Before
	public void setUp() {
		// 设置mock request的行为
		when(mockRequest.getRequestURI()).thenReturn("/v1/images/generations");

		mockProcessData = new EndpointProcessData();
		mockProcessData.setMock(false);
		mockProcessData.setPrivate(false);
		mockProcessData.setProtocol("openai");
		mockProcessData.setForwardUrl("http://test.com");
		mockProcessData.setAkCode("test-ak");
	}

	@Test(expected = BizParamCheckException.class)
	public void testGenerateImages_WhenModelIsNull_ShouldThrowBizParamCheckException() {
		ImagesRequest request = new ImagesRequest();
		request.setModel(null);

		try (MockedStatic<EndpointContext> mockedEndpointContext = mockStatic(EndpointContext.class);
			 MockedStatic<JacksonUtils> mockedJacksonUtils = mockStatic(JacksonUtils.class)) {

			mockedEndpointContext.when(EndpointContext::getRequest).thenReturn(mockRequest);
			mockedEndpointContext.when(EndpointContext::getProcessData).thenReturn(mockProcessData);

			when(router.route(eq("/v1/images/generations"), isNull(), any(), eq(false)))
				.thenThrow(new BizParamCheckException("没有可用渠道"));

			imagesController.generateImages(request);
		}
	}

	@Test(expected = BizParamCheckException.class)
	public void testGenerateImages_WhenModelIsEmpty_ShouldThrowBizParamCheckException() {
		ImagesRequest request = new ImagesRequest();
		request.setModel("");

		try (MockedStatic<EndpointContext> mockedEndpointContext = mockStatic(EndpointContext.class)) {
			mockedEndpointContext.when(EndpointContext::getRequest).thenReturn(mockRequest);
			mockedEndpointContext.when(EndpointContext::getProcessData).thenReturn(mockProcessData);

			when(router.route(eq("/v1/images/generations"), eq(""), any(), eq(false)))
				.thenThrow(new BizParamCheckException("没有可用渠道"));

			imagesController.generateImages(request);
		}
	}

	@Test(expected = BizParamCheckException.class)
	public void testGenerateImages_WhenModelIsBlank_ShouldThrowBizParamCheckException() {
		ImagesRequest request = new ImagesRequest();
		request.setModel("   ");

		try (MockedStatic<EndpointContext> mockedEndpointContext = mockStatic(EndpointContext.class)) {
			mockedEndpointContext.when(EndpointContext::getRequest).thenReturn(mockRequest);
			mockedEndpointContext.when(EndpointContext::getProcessData).thenReturn(mockProcessData);

			when(router.route(eq("/v1/images/generations"), eq("   "), any(), eq(false)))
				.thenThrow(new BizParamCheckException("没有可用渠道"));

			imagesController.generateImages(request);
		}
	}

	@Test
	public void testGenerateImages_WhenModelIsValid_ShouldReturnResponse() {
		ImagesRequest request = new ImagesRequest();
		request.setModel("dall-e-3");

		ChannelDB mockChannel = new ChannelDB();
		mockChannel.setChannelInfo("{\"apiKey\":\"test-key\"}");

		ImagesResponse expectedResponse = new ImagesResponse();
		ImagesProperty mockProperty = new ImagesProperty();

		try (MockedStatic<EndpointContext> mockedEndpointContext = mockStatic(EndpointContext.class);
			 MockedStatic<JacksonUtils> mockedJacksonUtils = mockStatic(JacksonUtils.class)) {

			mockedEndpointContext.when(EndpointContext::getRequest).thenReturn(mockRequest);
			mockedEndpointContext.when(EndpointContext::getProcessData).thenReturn(mockProcessData);

			when(router.route(eq("/v1/images/generations"), eq("dall-e-3"), any(), eq(false)))
				.thenReturn(mockChannel);

			when(adaptorManager.getProtocolAdaptor(
				eq("/v1/images/generations"),
				eq("openai"),
				eq(ImagesGeneratorAdaptor.class)))
				.thenReturn(mockAdaptor);

			mockedJacksonUtils.when(() -> JacksonUtils.deserialize(
					eq("{\"apiKey\":\"test-key\"}"),
					any(Class.class)))
				.thenReturn(mockProperty);

			when(mockAdaptor.getPropertyClass()).thenReturn((Class) ImagesProperty.class);
			when(mockAdaptor.generateImages(eq(request), eq("http://test.com"), eq(mockProperty)))
				.thenReturn(expectedResponse);

			ImagesResponse actualResponse = imagesController.generateImages(request);

			assertNotNull(actualResponse);
			assertEquals(expectedResponse, actualResponse);

			verify(router).route("/v1/images/generations", "dall-e-3", null, false);
			verify(limiterManager).incrementConcurrentCount("test-ak", "dall-e-3");
			verify(mockAdaptor).generateImages(request, "http://test.com", mockProperty);
		}
	}

	@Test
	public void testGenerateImages_WhenIsPrivate_ShouldNotIncrementLimiter() {
		ImagesRequest request = new ImagesRequest();
		request.setModel("dall-e-3");

		mockProcessData.setPrivate(true);

		ChannelDB mockChannel = new ChannelDB();
		mockChannel.setChannelInfo("{\"apiKey\":\"test-key\"}");

		ImagesResponse expectedResponse = new ImagesResponse();
		ImagesProperty mockProperty = new ImagesProperty();

		try (MockedStatic<EndpointContext> mockedEndpointContext = mockStatic(EndpointContext.class);
			 MockedStatic<JacksonUtils> mockedJacksonUtils = mockStatic(JacksonUtils.class)) {

			mockedEndpointContext.when(EndpointContext::getRequest).thenReturn(mockRequest);
			mockedEndpointContext.when(EndpointContext::getProcessData).thenReturn(mockProcessData);

			when(router.route(any(), any(), any(), anyBoolean())).thenReturn(mockChannel);
			when(adaptorManager.getProtocolAdaptor(any(), any(), any())).thenReturn(mockAdaptor);
			mockedJacksonUtils.when(() -> JacksonUtils.deserialize(anyString(), (Class) any(Class.class)))
				.thenReturn(mockProperty);
			when(mockAdaptor.getPropertyClass()).thenReturn((Class) ImagesProperty.class);
			when(mockAdaptor.generateImages(any(), any(), any())).thenReturn(expectedResponse);

			imagesController.generateImages(request);

			verify(limiterManager, never()).incrementConcurrentCount(any(), any());
		}
	}
}
