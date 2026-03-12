package com.ke.bella.openapi.limiter;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.limiter.QpsCheckResult;
import com.ke.bella.openapi.protocol.limiter.QpsRateLimit;
import com.ke.bella.openapi.protocol.limiter.QpsRateLimitAspect;
import com.ke.bella.openapi.protocol.limiter.manager.QpsLimiterManager;
import lombok.Data;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * QpsRateLimitAspect 单元测试
 */
@RunWith(MockitoJUnitRunner.class)
public class QpsRateLimitAspectTest {

    @Mock
    private QpsLimiterManager qpsLimiterManager;

    @Mock
    private ProceedingJoinPoint pjp;

    @Mock
    private MethodSignature methodSignature;

    private QpsRateLimitAspect aspect;

    @Before
    public void setUp() {
        // 使用构造方法注入
        aspect = new QpsRateLimitAspect(qpsLimiterManager);

        when(pjp.getSignature()).thenReturn(methodSignature);
    }

    /**
     * 测试：field 为空 -> 无条件限流
     */
    @Test
    public void testEmptyField_shouldAlwaysLimit() throws Throwable {
        QpsRateLimit annotation = mockAnnotation("", new String[]{});
        ApikeyInfo apikey = createApikey("ak_test", 100);

        setupBellaContext(apikey);
        mockQpsCheck(true, 50, 100);

        aspect.checkQpsLimit(pjp, annotation);

        verify(qpsLimiterManager).checkLimit("ak_test", 100);
        verify(pjp).proceed();
    }

    /**
     * 测试：field 非空，values 为空 -> 参数存在即限流
     */
    @Test
    public void testFieldOnly_paramExists_shouldLimit() throws Throwable {
        QpsRateLimit annotation = mockAnnotation("model", new String[]{});
        ApikeyInfo apikey = createApikey("ak_test", 200);

        when(methodSignature.getParameterNames()).thenReturn(new String[]{"model"});
        when(pjp.getArgs()).thenReturn(new Object[]{"any-model"});

        setupBellaContext(apikey);
        mockQpsCheck(true, 50, 200);

        aspect.checkQpsLimit(pjp, annotation);

        verify(qpsLimiterManager).checkLimit("ak_test", 200);
    }

    /**
     * 测试：field 非空，values 为空 -> 参数为 null 时不限流
     */
    @Test
    public void testFieldOnly_paramNull_shouldSkipLimit() throws Throwable {
        QpsRateLimit annotation = mockAnnotation("model", new String[]{});
        ApikeyInfo apikey = createApikey("ak_test", 200);

        when(methodSignature.getParameterNames()).thenReturn(new String[]{"model"});
        when(pjp.getArgs()).thenReturn(new Object[]{null});

        setupBellaContext(apikey);

        aspect.checkQpsLimit(pjp, annotation);

        verify(qpsLimiterManager, never()).checkLimit(anyString(), anyInt());
        verify(pjp).proceed();
    }

    /**
     * 测试：field 非空，values 为空 -> 对象字段存在即限流
     */
    @Test
    public void testFieldOnly_objectFieldExists_shouldLimit() throws Throwable {
        QpsRateLimit annotation = mockAnnotation("model", new String[]{});
        ApikeyInfo apikey = createApikey("ak_test", 200);

        TestRequest request = new TestRequest();
        request.setModel("any-model");

        when(methodSignature.getParameterNames()).thenReturn(new String[]{"request"});
        when(pjp.getArgs()).thenReturn(new Object[]{request});

        setupBellaContext(apikey);
        mockQpsCheck(true, 50, 200);

        aspect.checkQpsLimit(pjp, annotation);

        verify(qpsLimiterManager).checkLimit("ak_test", 200);
    }

    /**
     * 测试：简单参数匹配 - 参数值命中 values
     */
    @Test
    public void testSimpleParam_match_shouldLimit() throws Throwable {
        QpsRateLimit annotation = mockAnnotation("model", new String[]{"gpt-4", "claude-3"});
        ApikeyInfo apikey = createApikey("ak_test", 200);

        when(methodSignature.getParameterNames()).thenReturn(new String[]{"model"});
        when(pjp.getArgs()).thenReturn(new Object[]{"gpt-4"});

        setupBellaContext(apikey);
        mockQpsCheck(true, 150, 200);

        aspect.checkQpsLimit(pjp, annotation);

        verify(qpsLimiterManager).checkLimit("ak_test", 200);
    }

    /**
     * 测试：简单参数匹配 - 参数值不在 values 中 -> 不限流
     */
    @Test
    public void testSimpleParam_noMatch_shouldSkipLimit() throws Throwable {
        QpsRateLimit annotation = mockAnnotation("model", new String[]{"gpt-4", "claude-3"});
        ApikeyInfo apikey = createApikey("ak_test", 200);

        when(methodSignature.getParameterNames()).thenReturn(new String[]{"model"});
        when(pjp.getArgs()).thenReturn(new Object[]{"other-model"});

        setupBellaContext(apikey);

        aspect.checkQpsLimit(pjp, annotation);

        verify(qpsLimiterManager, never()).checkLimit(anyString(), anyInt());
        verify(pjp).proceed();
    }

    /**
     * 测试：对象字段匹配 - 从对象字段取值并命中
     */
    @Test
    public void testObjectField_match_shouldLimit() throws Throwable {
        QpsRateLimit annotation = mockAnnotation("model", new String[]{"gpt-4"});
        ApikeyInfo apikey = createApikey("ak_test", 200);

        TestRequest request = new TestRequest();
        request.setModel("gpt-4");

        when(methodSignature.getParameterNames()).thenReturn(new String[]{"request"});
        when(pjp.getArgs()).thenReturn(new Object[]{request});

        setupBellaContext(apikey);
        mockQpsCheck(true, 180, 200);

        aspect.checkQpsLimit(pjp, annotation);

        verify(qpsLimiterManager).checkLimit("ak_test", 200);
    }

    /**
     * 测试：QPS 超限 -> 抛出异常
     */
    @Test(expected = BellaException.RateLimitException.class)
    public void testQpsExceeded_shouldThrowException() throws Throwable {
        QpsRateLimit annotation = mockAnnotation("", new String[]{});
        ApikeyInfo apikey = createApikey("ak_test", 200);

        setupBellaContext(apikey);
        mockQpsCheck(false, 220, 200); // 超限

        aspect.checkQpsLimit(pjp, annotation);
    }

    /**
     * 测试：无 API Key -> 跳过限流
     */
    @Test
    public void testNoApikey_shouldSkipLimit() throws Throwable {
        QpsRateLimit annotation = mockAnnotation("", new String[]{});

        setupBellaContext(null);

        aspect.checkQpsLimit(pjp, annotation);

        verify(qpsLimiterManager, never()).checkLimit(anyString(), anyInt());
        verify(pjp).proceed();
    }

    // ========== Helper Methods ==========

    private QpsRateLimit mockAnnotation(String field, String[] values) {
        QpsRateLimit annotation = mock(QpsRateLimit.class);
        when(annotation.field()).thenReturn(field);
        when(annotation.values()).thenReturn(values);
        return annotation;
    }

    private ApikeyInfo createApikey(String code, Integer qpsLimit) {
        ApikeyInfo apikey = new ApikeyInfo();
        apikey.setCode(code);
        apikey.setQpsLimit(qpsLimit);
        return apikey;
    }

    private void setupBellaContext(ApikeyInfo apikey) {
        BellaContext.setApikey(apikey);
    }

    private void mockQpsCheck(boolean allowed, long currentQps, int limit) {
        QpsCheckResult result = new QpsCheckResult(allowed, currentQps, limit);
        when(qpsLimiterManager.checkLimit(anyString(), anyInt())).thenReturn(result);
    }

    /**
     * 测试用的请求对象
     */
    @Data
    static class TestRequest {
        private String model;
        private String prompt;
    }
}
