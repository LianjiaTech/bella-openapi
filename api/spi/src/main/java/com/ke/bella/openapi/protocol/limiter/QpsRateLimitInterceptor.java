package com.ke.bella.openapi.protocol.limiter;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.limiter.manager.QpsLimiterManager;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

/**
 * QPS 限流拦截器
 * 基于滑动窗口算法实现精确的 QPS 限流，在请求到达业务逻辑前进行拦截
 * 拦截器顺序: AuthorizationInterceptor -> QpsRateLimitInterceptor -> MonthQuotaInterceptor
 */
public class QpsRateLimitInterceptor extends HandlerInterceptorAdapter {

    private final QpsLimiterManager qpsLimiterManager;

    public QpsRateLimitInterceptor(QpsLimiterManager qpsLimiterManager) {
        this.qpsLimiterManager = qpsLimiterManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跳过异步请求标记（避免重复处理）
        if (Boolean.TRUE.equals(request.getAttribute(ASYNC_REQUEST_MARKER))) {
            return true;
        }

        // 获取当前 APIKey 信息
        ApikeyInfo apikey = BellaContext.getApikeyIgnoreNull();
        if(apikey == null) {
            return true;
        }

        // 执行 QPS 限流检查（Manager 内部处理默认值和开关逻辑）
        String akCode = apikey.getCode();
        QpsCheckResult result = qpsLimiterManager.checkLimit(akCode, apikey.getQpsLimit());

        if(!result.isAllowed()) {
            // 添加 Retry-After 响应头，建议客户端 1 秒后重试
            response.setHeader("Retry-After", "1");
            throw new BellaException.RateLimitException(
                    String.format("QPS 超过限制（当前: %d, 限制: %d），请 1 秒后重试",
                            result.getCurrentQps(), result.getLimit()));
        }

        return true;
    }
}
