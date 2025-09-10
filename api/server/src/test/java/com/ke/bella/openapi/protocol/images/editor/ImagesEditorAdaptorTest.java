package com.ke.bella.openapi.protocol.images.editor;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.images.ImageDataType;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImagesEditorAdaptorTest {

	@Mock
	private ImagesEditorProperty property;

	@Mock
	private MultipartFile multipartFile;

	private ImagesEditRequest request;
	private TestImagesEditorAdaptor adaptor;

	// 创建一个测试用的实现类
	private static class TestImagesEditorAdaptor implements ImagesEditorAdaptor<ImagesEditorProperty> {
		@Override
		public String endpoint() {
			return "/test";
		}

		@Override
		public String getDescription() {
			return "test";
		}

		@Override
		public Class<?> getPropertyClass() {
			return ImagesEditorProperty.class;
		}

		@Override
		public ImagesResponse doEditImages(ImagesEditRequest request, String url, ImagesEditorProperty property, ImageDataType dataType) throws IOException {
			return null;
		}
	}

	@BeforeEach
	void setUp() {
		request = new ImagesEditRequest();
		adaptor = new TestImagesEditorAdaptor();
	}

	@Test
	void testProcessImageData_WithBase64_ShouldReturnBase64Type() throws IOException {
		// 设置支持Base64且提供了Base64数据
		when(property.isSupportBase64()).thenReturn(true);
		request.setImage_b64_json("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.BASE64, result);
		verify(property).isSupportBase64();
	}

	@Test
	void testProcessImageData_WithUrl_ShouldReturnUrlType() throws IOException {
		// 设置不支持Base64但支持URL，且提供了URL
		when(property.isSupportBase64()).thenReturn(false);
		when(property.isSupportUrl()).thenReturn(true);
		request.setImage_url("http://example.com/image.jpg");

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.URL, result);
		verify(property).isSupportBase64();
		verify(property).isSupportUrl();
	}

	@Test
	void testProcessImageData_WithFile_ShouldReturnFileType() throws IOException {
		// 设置不支持Base64和URL，但支持文件上传
		when(property.isSupportBase64()).thenReturn(false);
		when(property.isSupportUrl()).thenReturn(false);
		when(property.isSupportFile()).thenReturn(true);
		when(multipartFile.isEmpty()).thenReturn(false);

		request.setImage(multipartFile);

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.FILE, result);
		verify(property).isSupportBase64();
		verify(property).isSupportUrl();
		verify(property).isSupportFile();
		verify(multipartFile).isEmpty();
	}

	@Test
	void testProcessImageData_WithFileConvertToBase64_ShouldReturnBase64Type() throws IOException {
		// 设置不支持文件上传但支持Base64，提供文件时应转换为Base64
		when(property.isSupportBase64()).thenReturn(true);
		when(property.isSupportUrl()).thenReturn(false);
		when(property.isSupportFile()).thenReturn(false);
		when(multipartFile.isEmpty()).thenReturn(false);
		when(multipartFile.getBytes()).thenReturn("test image data".getBytes());
		when(multipartFile.getContentType()).thenReturn("image/png");

		request.setImage(multipartFile);

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.BASE64, result);

		// 验证Base64数据被正确设置
		String expectedBase64 = Base64.getEncoder().encodeToString("test image data".getBytes());
		String expectedImageData = String.format("data:image/%s;base64,%s", "png", expectedBase64);
		assertEquals(expectedImageData, request.getImage_b64_json());

		verify(multipartFile).getBytes();
		verify(multipartFile).getContentType();
	}

	@Test
	void testProcessImageData_WithFileConvertToBase64_DefaultPngFormat() throws IOException {
		// 测试文件没有Content-Type时默认使用png格式
		when(property.isSupportBase64()).thenReturn(true);
		when(property.isSupportUrl()).thenReturn(false);
		when(property.isSupportFile()).thenReturn(false);
		when(multipartFile.isEmpty()).thenReturn(false);
		when(multipartFile.getBytes()).thenReturn("test image data".getBytes());
		when(multipartFile.getContentType()).thenReturn(null);

		request.setImage(multipartFile);

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.BASE64, result);

		// 验证使用默认的png格式
		String expectedBase64 = Base64.getEncoder().encodeToString("test image data".getBytes());
		String expectedImageData = String.format("data:image/%s;base64,%s", "png", expectedBase64);
		assertEquals(expectedImageData, request.getImage_b64_json());
	}

	@Test
	void testProcessImageData_WithFileConvertToBase64_NonImageContentType() throws IOException {
		// 测试非图片Content-Type时默认使用png格式
		when(property.isSupportBase64()).thenReturn(true);
		when(property.isSupportUrl()).thenReturn(false);
		when(property.isSupportFile()).thenReturn(false);
		when(multipartFile.isEmpty()).thenReturn(false);
		when(multipartFile.getBytes()).thenReturn("test image data".getBytes());
		when(multipartFile.getContentType()).thenReturn("application/octet-stream");

		request.setImage(multipartFile);

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.BASE64, result);

		// 验证使用默认的png格式
		String expectedBase64 = Base64.getEncoder().encodeToString("test image data".getBytes());
		String expectedImageData = String.format("data:image/%s;base64,%s", "png", expectedBase64);
		assertEquals(expectedImageData, request.getImage_b64_json());
	}

	@Test
	void testProcessImageData_NoValidInput_ShouldThrowException() {
		// 测试没有提供任何有效输入时抛出异常
		when(property.isSupportBase64()).thenReturn(true);
		when(property.isSupportUrl()).thenReturn(true);
		when(property.isSupportFile()).thenReturn(true);

		BizParamCheckException exception = assertThrows(BizParamCheckException.class, () -> {
			adaptor.processImageData(request, property);
		});

		assertTrue(exception.getMessage().contains("请求参数格式错误，请使用以下支持的图像上传方式"));
		assertTrue(exception.getMessage().contains("文件上传"));
		assertTrue(exception.getMessage().contains("URL链接"));
		assertTrue(exception.getMessage().contains("Base64编码"));
	}

	@Test
	void testProcessImageData_OnlySupportBase64_ErrorMessage() {
		// 测试只支持Base64时的错误信息
		when(property.isSupportBase64()).thenReturn(true);
		when(property.isSupportUrl()).thenReturn(false);
		when(property.isSupportFile()).thenReturn(false);

		BizParamCheckException exception = assertThrows(BizParamCheckException.class, () -> {
			adaptor.processImageData(request, property);
		});

		assertTrue(exception.getMessage().contains("Base64编码"));
		assertFalse(exception.getMessage().contains("文件上传"));
		assertFalse(exception.getMessage().contains("URL链接"));
	}

	@Test
	void testProcessImageData_OnlySupportUrl_ErrorMessage() {
		// 测试只支持URL时的错误信息
		when(property.isSupportBase64()).thenReturn(false);
		when(property.isSupportUrl()).thenReturn(true);
		when(property.isSupportFile()).thenReturn(false);

		BizParamCheckException exception = assertThrows(BizParamCheckException.class, () -> {
			adaptor.processImageData(request, property);
		});

		assertTrue(exception.getMessage().contains("URL链接"));
		assertFalse(exception.getMessage().contains("文件上传"));
		assertFalse(exception.getMessage().contains("Base64编码"));
	}

	@Test
	void testProcessImageData_OnlySupportFile_ErrorMessage() {
		// 测试只支持文件上传时的错误信息
		when(property.isSupportBase64()).thenReturn(false);
		when(property.isSupportUrl()).thenReturn(false);
		when(property.isSupportFile()).thenReturn(true);

		BizParamCheckException exception = assertThrows(BizParamCheckException.class, () -> {
			adaptor.processImageData(request, property);
		});

		assertTrue(exception.getMessage().contains("文件上传"));
		assertFalse(exception.getMessage().contains("URL链接"));
		assertFalse(exception.getMessage().contains("Base64编码"));
	}

	@Test
	void testProcessImageData_EmptyBase64String_ShouldSkip() throws IOException {
		// 测试空的Base64字符串应该被跳过
		when(property.isSupportBase64()).thenReturn(true);
		when(property.isSupportUrl()).thenReturn(true);
		request.setImage_b64_json("");
		request.setImage_url("http://example.com/image.jpg");

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.URL, result);
	}

	@Test
	void testProcessImageData_EmptyUrlString_ShouldSkip() throws IOException {
		// 测试空的URL字符串应该被跳过
		when(property.isSupportBase64()).thenReturn(false);
		when(property.isSupportUrl()).thenReturn(true);
		when(property.isSupportFile()).thenReturn(true);
		when(multipartFile.isEmpty()).thenReturn(false);

		request.setImage_url("");
		request.setImage(multipartFile);

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.FILE, result);
	}

	@Test
	void testProcessImageData_EmptyMultipartFile_ShouldSkip() {
		// 测试空的MultipartFile应该被跳过
		when(property.isSupportBase64()).thenReturn(false);
		when(property.isSupportUrl()).thenReturn(false);
		when(property.isSupportFile()).thenReturn(true);
		when(multipartFile.isEmpty()).thenReturn(true);

		request.setImage(multipartFile);

		BizParamCheckException exception = assertThrows(BizParamCheckException.class, () -> {
			adaptor.processImageData(request, property);
		});

		assertTrue(exception.getMessage().contains("请求参数格式错误"));
	}

	@Test
	void testProcessImageData_PriorityOrder_Base64First() throws IOException {
		// 测试优先级：Base64 > URL > File
		when(property.isSupportBase64()).thenReturn(true);
		when(property.isSupportUrl()).thenReturn(true);
		when(property.isSupportFile()).thenReturn(true);

		request.setImage_b64_json("data:image/png;base64,test");
		request.setImage_url("http://example.com/image.jpg");
		request.setImage(multipartFile);

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.BASE64, result);
		// 验证只检查了Base64支持，没有检查其他的
		verify(property).isSupportBase64();
		verify(property, never()).isSupportUrl();
		verify(property, never()).isSupportFile();
	}

	@Test
	void testProcessImageData_PriorityOrder_UrlSecond() throws IOException {
		// 测试优先级：URL > File（当Base64不可用时）
		when(property.isSupportBase64()).thenReturn(false);
		when(property.isSupportUrl()).thenReturn(true);
		when(property.isSupportFile()).thenReturn(true);

		request.setImage_url("http://example.com/image.jpg");
		request.setImage(multipartFile);

		ImageDataType result = adaptor.processImageData(request, property);

		assertEquals(ImageDataType.URL, result);
		verify(property).isSupportBase64();
		verify(property).isSupportUrl();
		verify(property, never()).isSupportFile();
	}
}
