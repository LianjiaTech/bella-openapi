package com.ke.bella.openapi.protocol.completion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ke.bella.openapi.protocol.Callbacks;

@ExtendWith(MockitoExtension.class)
class GoogleAdaptorTest {

    @InjectMocks
    private GoogleAdaptor googleAdaptor;

    @Mock
    private OpenAIAdaptor openAIAdaptor;

    private CompletionRequest request;
    private OpenAIProperty property;
    private String testUrl;

    @BeforeEach
    void setUp() {
        request = new CompletionRequest();
        request.setModel("gemini-2.0-flash-thinking-exp");

        property = new OpenAIProperty();
        property.setDeployName("gemini-2.0-flash-thinking-exp");

        testUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-thinking-exp:generateContent";
    }

    // ========== 基础场景测试（null安全） ==========

    @Test
    void testNormalizeUsage_WithNullResponse_ShouldReturnSafely() throws Exception {
        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(googleAdaptor, (CompletionResponse) null));
    }

    @Test
    void testNormalizeUsage_WithNullUsage_ShouldReturnSafely() throws Exception {
        CompletionResponse response = CompletionResponse.builder()
                .usage(null)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(googleAdaptor, response));
        assertNull(response.getUsage());
    }

    @Test
    void testNormalizeUsage_WithNullDetails_ShouldNotModify() throws Exception {
        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(1240)
                .completion_tokens(4)
                .total_tokens(1244)
                .completion_tokens_details(null)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        assertEquals(4, response.getUsage().getCompletion_tokens());
        assertEquals(1244, response.getUsage().getTotal_tokens());
    }

    // ========== Google reasoning tokens修正测试 ==========

    @Test
    void testNormalizeUsage_WithReasoningTokens_ShouldAddToCompletionTokens() throws Exception {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(863);
        details.setCached_tokens(0);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(1240)
                .completion_tokens(4)
                .total_tokens(2107)
                .cache_creation_tokens(0)
                .cache_read_tokens(0)
                .completion_tokens_details(details)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        assertEquals(867, response.getUsage().getCompletion_tokens(),
                "completion_tokens should be 4 + (863+0+0) = 867");
        assertEquals(2107, response.getUsage().getTotal_tokens(),
                "total_tokens should be 1240 + 867 = 2107");
    }

    @Test
    void testNormalizeUsage_CompletionLessThanReasoning_ShouldNormalize() throws Exception {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(500);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(100)
                .completion_tokens(200)
                .total_tokens(300)
                .completion_tokens_details(details)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        assertEquals(700, response.getUsage().getCompletion_tokens(),
                "completion_tokens should be 200 + 500 = 700");
        assertEquals(800, response.getUsage().getTotal_tokens(),
                "total_tokens should be 100 + 700 = 800");
    }

    @Test
    void testNormalizeUsage_AlwaysAddsDetailTokens() throws Exception {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(500);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(100)
                .completion_tokens(50)
                .total_tokens(150)
                .completion_tokens_details(details)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        assertEquals(550, response.getUsage().getCompletion_tokens(),
                "completion_tokens should always add detailsTotal (50 + 500 = 550)");
        assertEquals(650, response.getUsage().getTotal_tokens(),
                "total_tokens should be 100 + 550 = 650");
    }

    @Test
    void testNormalizeUsage_WithAudioAndImageTokens_ShouldAddAll() throws Exception {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(100);
        details.setAudio_tokens(50);
        details.setImage_tokens(30);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(200)
                .completion_tokens(10)
                .total_tokens(210)
                .completion_tokens_details(details)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        assertEquals(190, response.getUsage().getCompletion_tokens(),
                "completion_tokens should be 10 + (100+50+30) = 190");
        assertEquals(390, response.getUsage().getTotal_tokens(),
                "total_tokens should be 200 + 190 = 390");
    }

    @Test
    void testNormalizeUsage_WithOnlyAudioTokens_ShouldAdd() throws Exception {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(0);
        details.setAudio_tokens(120);
        details.setImage_tokens(0);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(100)
                .completion_tokens(20)
                .total_tokens(120)
                .completion_tokens_details(details)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        assertEquals(140, response.getUsage().getCompletion_tokens(),
                "completion_tokens should be 20 + 120 = 140");
        assertEquals(240, response.getUsage().getTotal_tokens());
    }

    @Test
    void testNormalizeUsage_WithOnlyImageTokens_ShouldAdd() throws Exception {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(0);
        details.setAudio_tokens(0);
        details.setImage_tokens(80);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(150)
                .completion_tokens(15)
                .total_tokens(165)
                .completion_tokens_details(details)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        assertEquals(95, response.getUsage().getCompletion_tokens(),
                "completion_tokens should be 15 + 80 = 95");
        assertEquals(245, response.getUsage().getTotal_tokens());
    }

    // ========== 边界条件测试 ==========

    @Test
    void testNormalizeUsage_WithZeroReasoningTokens_ShouldNotModify() throws Exception {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(0);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(100)
                .completion_tokens(50)
                .total_tokens(150)
                .completion_tokens_details(details)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        assertEquals(50, response.getUsage().getCompletion_tokens());
        assertEquals(150, response.getUsage().getTotal_tokens());
    }


    @Test
    void testNormalizeUsage_TotalTokensUpdated_ShouldBeCorrect() throws Exception {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(300);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(500)
                .completion_tokens(100)
                .total_tokens(600)
                .completion_tokens_details(details)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", CompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        int expectedCompletion = 100 + 300;
        int expectedTotal = 500 + expectedCompletion;

        assertEquals(expectedCompletion, response.getUsage().getCompletion_tokens());
        assertEquals(expectedTotal, response.getUsage().getTotal_tokens());
    }

    // ========== StreamCompletionResponse测试 ==========

    @Test
    void testNormalizeUsage_StreamResponse_WithReasoningTokens_ShouldNormalize() throws Exception {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(863);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(1240)
                .completion_tokens(4)
                .total_tokens(2107)
                .completion_tokens_details(details)
                .build();

        StreamCompletionResponse response = StreamCompletionResponse.builder()
                .usage(usage)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", StreamCompletionResponse.class);
        method.setAccessible(true);
        method.invoke(googleAdaptor, response);

        assertEquals(867, response.getUsage().getCompletion_tokens());
        assertEquals(2107, response.getUsage().getTotal_tokens());
    }

    @Test
    void testNormalizeUsage_StreamResponse_WithNullUsage_ShouldReturnSafely() throws Exception {
        StreamCompletionResponse response = StreamCompletionResponse.builder()
                .usage(null)
                .build();

        Method method = GoogleAdaptor.class.getDeclaredMethod("normalizeGoogleUsage", StreamCompletionResponse.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(googleAdaptor, response));
        assertNull(response.getUsage());
    }

    // ========== 集成测试 ==========

    @Test
    void testCompletion_WithGoogleUsage_ShouldNormalize() {
        CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
        details.setReasoning_tokens(863);

        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(1240)
                .completion_tokens(4)
                .total_tokens(2107)
                .completion_tokens_details(details)
                .build();

        CompletionResponse mockResponse = CompletionResponse.builder()
                .usage(usage)
                .build();

        when(openAIAdaptor.completion(any(CompletionRequest.class), eq(testUrl), any(OpenAIProperty.class)))
                .thenReturn(mockResponse);

        CompletionResponse result = googleAdaptor.completion(request, testUrl, property);

        assertNotNull(result);
        assertEquals(867, result.getUsage().getCompletion_tokens(),
                "Google usage should be normalized");
        assertEquals(2107, result.getUsage().getTotal_tokens());

        verify(openAIAdaptor, times(1)).completion(any(CompletionRequest.class), eq(testUrl), any(OpenAIProperty.class));
    }

    @Test
    void testCompletion_WithoutReasoningTokens_ShouldNotModify() {
        CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                .prompt_tokens(100)
                .completion_tokens(50)
                .total_tokens(150)
                .build();

        CompletionResponse mockResponse = CompletionResponse.builder()
                .usage(usage)
                .build();

        when(openAIAdaptor.completion(any(CompletionRequest.class), eq(testUrl), any(OpenAIProperty.class)))
                .thenReturn(mockResponse);

        CompletionResponse result = googleAdaptor.completion(request, testUrl, property);

        assertNotNull(result);
        assertEquals(50, result.getUsage().getCompletion_tokens());
        assertEquals(150, result.getUsage().getTotal_tokens());
    }

    @Test
    void testStreamCompletion_ShouldWrapCallbackAndNormalize() {
        Callbacks.StreamCompletionCallback mockCallback = mock(Callbacks.StreamCompletionCallback.class);

        doAnswer(invocation -> {
            Callbacks.StreamCompletionCallback wrappedCallback = invocation.getArgument(3);

            CompletionResponse.TokensDetail details = new CompletionResponse.TokensDetail();
            details.setReasoning_tokens(863);

            CompletionResponse.TokenUsage usage = CompletionResponse.TokenUsage.builder()
                    .prompt_tokens(1240)
                    .completion_tokens(4)
                    .total_tokens(2107)
                    .completion_tokens_details(details)
                    .build();

            StreamCompletionResponse event = StreamCompletionResponse.builder()
                    .usage(usage)
                    .build();

            wrappedCallback.callback(event);

            assertEquals(867, event.getUsage().getCompletion_tokens(),
                    "Usage should be normalized in callback");

            return null;
        }).when(openAIAdaptor).streamCompletion(
                any(CompletionRequest.class),
                eq(testUrl),
                any(OpenAIProperty.class),
                any(Callbacks.StreamCompletionCallback.class));

        googleAdaptor.streamCompletion(request, testUrl, property, mockCallback);

        verify(openAIAdaptor, times(1)).streamCompletion(
                any(CompletionRequest.class),
                eq(testUrl),
                any(OpenAIProperty.class),
                any(Callbacks.StreamCompletionCallback.class));
    }

    @Test
    void testGetDescription() {
        String description = googleAdaptor.getDescription();
        assertEquals("Google扩展OpenAI协议", description);
    }

    @Test
    void testGetPropertyClass() {
        when(openAIAdaptor.getPropertyClass()).thenReturn(OpenAIProperty.class);

        Class<?> propertyClass = googleAdaptor.getPropertyClass();
        assertEquals(OpenAIProperty.class, propertyClass);

        verify(openAIAdaptor, times(1)).getPropertyClass();
    }
}
