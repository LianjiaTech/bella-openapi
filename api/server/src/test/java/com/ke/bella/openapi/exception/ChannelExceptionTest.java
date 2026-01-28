package com.ke.bella.openapi.exception;

import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试异常类型是否正确区分系统异常和渠道异常
 */
public class ChannelExceptionTest {

    /**
     * 测试：渠道异常应该返回 "channel_error" 类型
     */
    @Test
    public void testChannelException_ShouldReturnChannelErrorType() {
        // 模拟 AWS Bedrock 返回 400 错误
        ChannelException exception = new ChannelException.OpenAIException(
            400,
            "channel_error",
            "ValidationException: max_tokens must be greater than thinking.budget_tokens"
        );

        // 转换为 API 响应
        OpenapiResponse.OpenapiError error = exception.convertToOpenapiError();

        // 断言
        assertEquals("channel_error", error.getType(), "渠道异常的 type 应该是 channel_error");
        assertEquals(400, error.getHttpCode(), "HTTP 状态码应该是 400");
        assertNotNull(error.getMessage(), "错误信息不应为空");
        assertTrue(error.getMessage().contains("ValidationException"), "错误信息应包含原始异常信息");
    }

    /**
     * 测试：系统异常应该返回 "Internal Exception" 类型
     */
    @Test
    public void testSystemException_ShouldReturnInternalExceptionType() {
        // 模拟系统参数校验失败
        ChannelException exception = ChannelException.fromResponse(400, "No audio data to send");

        // 转换为 API 响应
        OpenapiResponse.OpenapiError error = exception.convertToOpenapiError();

        // 断言
        assertEquals("Internal Exception", error.getType(), "系统异常的 type 应该是 Internal Exception");
        assertEquals(400, error.getHttpCode(), "HTTP 状态码应该是 400");
        assertEquals("No audio data to send", error.getMessage(), "错误信息应该匹配");
    }

    /**
     * 测试：渠道异常保留原始状态码（包括 5xx）
     */
    @Test
    public void testChannelException_PreserveOriginalStatusCode() {
        // 模拟渠道返回 500 错误
        ChannelException exception = new ChannelException.OpenAIException(
            500,
            "channel_error",
            "Internal server error"
        );

        OpenapiResponse.OpenapiError error = exception.convertToOpenapiError();

        // 断言：应该保留原始状态码 500（不转换为 503）
        assertEquals(500, error.getHttpCode(), "应该保留渠道返回的原始状态码 500");
        assertEquals("channel_error", error.getType(), "type 应该保持为 channel_error");
    }

    /**
     * 测试：fromException 对 BellaException 的处理
     */
    @Test
    public void testFromException_ShouldPreserveBellaException() {
        // 创建一个 OpenAIException
        ChannelException original = new ChannelException.OpenAIException(
            400,
            "channel_error",
            "Invalid request"
        );

        // 通过 fromException 转换
        ChannelException converted = ChannelException.fromException(original);

        // 断言：应该返回原始对象，不是新的匿名类
        assertSame(original, converted, "fromException 应该直接返回原始的 ChannelException");

        OpenapiResponse.OpenapiError error = converted.convertToOpenapiError();
        assertEquals("channel_error", error.getType(), "type 应该保持为 channel_error");
    }

    /**
     * 测试：fromException 对标准 Java 异常的处理
     */
    @Test
    public void testFromException_JavaException_ShouldReturnInternalException() {
        // 创建一个标准异常
        Exception javaException = new IllegalArgumentException("Invalid parameter");

        // 通过 fromException 转换
        ChannelException converted = ChannelException.fromException(javaException);

        // 断言
        assertEquals(400, converted.getHttpCode(), "IllegalArgumentException 应该转为 400");
        assertEquals("Illegal Argument", converted.getType(), "type 应该是 Illegal Argument");
    }

    /**
     * 测试：不同异常类型的 type 值
     */
    @Test
    public void testDifferentExceptionTypes() {
        // 1. 限流异常
        ChannelException rateLimitEx = new ChannelException.RateLimitException("Rate limit exceeded");
        assertEquals("Too Many Requests", rateLimitEx.getType());
        assertEquals(429, rateLimitEx.getHttpCode());

        // 2. 权限异常
        ChannelException authEx = new ChannelException.AuthorizationException("Unauthorized");
        assertEquals("Unauthorized", authEx.getType());
        assertEquals(401, authEx.getHttpCode());

        // 3. 安全检查异常
        ChannelException safetyEx = new ChannelException.SafetyCheckException("sensitive data");
        assertEquals("safety_check", safetyEx.getType());
        assertEquals(400, safetyEx.getHttpCode());
    }

    /**
     * 测试：convertToOpenapiError 的分支逻辑
     */
    @Test
    public void testConvertToOpenapiError_DifferentBranches() {
        // 分支1：OpenAIException
        ChannelException openaiEx = new ChannelException.OpenAIException(400, "invalid_request", "Bad request");
        OpenapiResponse.OpenapiError error1 = openaiEx.convertToOpenapiError();
        assertEquals("invalid_request", error1.getType());

        // 分支2：SafetyCheckException
        ChannelException safetyEx = new ChannelException.SafetyCheckException("sensitive");
        OpenapiResponse.OpenapiError error2 = safetyEx.convertToOpenapiError();
        assertEquals("safety_check", error2.getType());
        assertNotNull(error2.getSensitive());

        // 分支3：其他异常（如 RateLimitException）
        ChannelException rateEx = new ChannelException.RateLimitException("Too many requests");
        OpenapiResponse.OpenapiError error3 = rateEx.convertToOpenapiError();
        assertEquals("Too Many Requests", error3.getType());
    }

    /**
     * 实际场景测试：模拟 AWS Bedrock 返回 ValidationException
     * （这是你案例中的场景）
     */
    @Test
    public void testRealScenario_AwsBedrockValidationError() {
        // 模拟 AWS Bedrock 返回的错误
        String awsErrorMessage = "software.amazon.awssdk.services.bedrockruntime.model.ValidationException: " +
                "`max_tokens` must be greater than `thinking.budget_tokens`. " +
                "Please consult our documentation at https://docs.claude.com/en/docs/build-with-claude/extended-thinking";

        // 使用 OpenAIException 创建渠道异常
        ChannelException exception = new ChannelException.OpenAIException(
            400,
            "channel_error",
            awsErrorMessage
        );

        // 转换为 API 响应
        OpenapiResponse.OpenapiError error = exception.convertToOpenapiError();

        // 断言：这应该是渠道异常，不是系统异常
        assertEquals("channel_error", error.getType(),
            "AWS Bedrock 返回的错误应该被标记为 channel_error，而不是 Internal Exception");
        assertEquals(400, error.getHttpCode());
        assertEquals("400", error.getCode());
        assertTrue(error.getMessage().contains("ValidationException"));
    }

    /**
     * 测试：fromException 对 UnsupportedOperationException 的处理
     */
    @Test
    public void testFromException_UnsupportedOperation() {
        Exception unsupportedEx = new UnsupportedOperationException("Operation not supported");

        ChannelException converted = ChannelException.fromException(unsupportedEx);

        assertEquals(400, converted.getHttpCode());
        assertEquals("Unsupported Operation", converted.getType());
        assertEquals("Operation not supported", converted.getMessage());
    }

    /**
     * 测试：fromException 对 IOException 的处理（应返回 502）
     */
    @Test
    public void testFromException_IOException_ShouldReturn502() {
        Exception ioEx = new java.io.IOException("Connection timeout");

        ChannelException converted = ChannelException.fromException(ioEx);

        assertEquals(502, converted.getHttpCode(), "IOException 应该返回 502 Bad Gateway");
        assertEquals("Internal Exception", converted.getType());
        assertEquals("Connection timeout", converted.getMessage());
    }

    /**
     * 测试：fromException 对普通异常的处理（应返回 500）
     */
    @Test
    public void testFromException_GenericException_ShouldReturn500() {
        Exception genericEx = new RuntimeException("Unexpected error");

        ChannelException converted = ChannelException.fromException(genericEx);

        assertEquals(500, converted.getHttpCode(), "普通异常应该返回 500");
        assertEquals("Internal Exception", converted.getType());
        assertEquals("Unexpected error", converted.getMessage());
    }

    /**
     * 测试：fromException 对 ServletException 的处理
     */
    @Test
    public void testFromException_ServletException() {
        Exception servletEx = new javax.servlet.ServletException("Servlet error");

        ChannelException converted = ChannelException.fromException(servletEx);

        assertEquals(400, converted.getHttpCode());
        assertEquals("Internal Exception", converted.getType());
    }

    /**
     * 测试：fromException 对嵌套的 ChannelException 的处理
     */
    @Test
    public void testFromException_NestedChannelException() {
        // 创建一个嵌套的异常
        ChannelException innerException = new ChannelException.OpenAIException(
            400,
            "channel_error",
            "Original channel error"
        );

        Exception wrapperException = new RuntimeException("Wrapper", innerException);

        // 通过 fromException 转换
        ChannelException converted = ChannelException.fromException(wrapperException);

        // 断言：应该返回内部的 ChannelException
        assertSame(innerException, converted, "应该提取出嵌套的 ChannelException");
        assertEquals("channel_error", converted.getType());
    }

    /**
     * 测试：OpenAIException 对 5xx 错误的处理（应该转换为 503）
     */
    @Test
    public void testOpenAIException_5xxErrors_ConvertTo503() {
        // 测试 500
        ChannelException ex500 = new ChannelException.OpenAIException(
            500,
            "channel_error",
            "Internal server error"
        );
        assertEquals(503, ex500.getHttpCode(), "500 应该转换为 503");

        // 测试 502
        ChannelException ex502 = new ChannelException.OpenAIException(
            502,
            "channel_error",
            "Bad gateway"
        );
        assertEquals(503, ex502.getHttpCode(), "502 应该转换为 503");

        // 测试 504
        ChannelException ex504 = new ChannelException.OpenAIException(
            504,
            "channel_error",
            "Gateway timeout"
        );
        assertEquals(503, ex504.getHttpCode(), "504 应该转换为 503");
    }

    /**
     * 测试：OpenAIException 对 4xx 错误的处理（应该保持原状态码）
     */
    @Test
    public void testOpenAIException_4xxErrors_PreserveCode() {
        ChannelException ex400 = new ChannelException.OpenAIException(
            400,
            "invalid_request_error",
            "Bad request"
        );
        assertEquals(400, ex400.getHttpCode(), "400 应该保持原样");

        ChannelException ex404 = new ChannelException.OpenAIException(
            404,
            "not_found",
            "Not found"
        );
        assertEquals(404, ex404.getHttpCode(), "404 应该保持原样");
    }

    /**
     * 测试：SafetyCheckException 的 sensitive 字段
     */
    @Test
    public void testSafetyCheckException_SensitiveField() {
        Object sensitiveData = new Object() {
            public String field = "sensitive content";
        };

        ChannelException.SafetyCheckException exception =
            new ChannelException.SafetyCheckException(sensitiveData);

        OpenapiResponse.OpenapiError error = exception.convertToOpenapiError();

        assertEquals("safety_check", error.getType());
        assertEquals(400, error.getHttpCode());
        assertEquals("safety_check_no_pass", error.getMessage());
        assertSame(sensitiveData, error.getSensitive(), "sensitive 字段应该被保留");
    }

    /**
     * 测试：ClientNotLoginException
     */
    @Test
    public void testClientNotLoginException() {
        String redirectUrl = "https://example.com/login";
        ChannelException.ClientNotLoginException exception =
            new ChannelException.ClientNotLoginException(redirectUrl);

        assertEquals(401, exception.getHttpCode());
        assertEquals("No Login", exception.getType());
        assertEquals("Need to login", exception.getMessage());
        assertEquals(redirectUrl, exception.getRedirectUrl());
    }

    /**
     * 测试：convertToOpenapiError 对 null error 参数的处理
     */
    @Test
    public void testOpenAIException_WithNullError() {
        ChannelException exception = new ChannelException.OpenAIException(
            400,
            "test_error",
            "Test message",
            null
        );

        OpenapiResponse.OpenapiError error = exception.convertToOpenapiError();

        assertNotNull(error, "即使传入 null error，也应该创建一个新的 error 对象");
        assertEquals("test_error", error.getType());
        assertEquals("Test message", error.getMessage());
        assertEquals(400, error.getHttpCode());
    }

    /**
     * 测试：fromResponse 和 fromException 的区别
     */
    @Test
    public void testDifferenceBetweenFromResponseAndFromException() {
        // fromResponse: 用于创建系统级响应错误
        ChannelException fromResponse = ChannelException.fromResponse(400, "System error");
        assertEquals("Internal Exception", fromResponse.getType());
        assertEquals(400, fromResponse.getHttpCode());

        // fromException: 用于包装 Java 异常
        ChannelException fromException = ChannelException.fromException(
            new IllegalArgumentException("Invalid param")
        );
        assertEquals("Illegal Argument", fromException.getType());
        assertEquals(400, fromException.getHttpCode());

        // 两者的类型和处理方式不同
        assertNotEquals(fromResponse.getType(), fromException.getType());
    }
}
