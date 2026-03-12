package com.ke.bella.openapi.protocol.limiter;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.limiter.manager.QpsLimiterManager;
import com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
        if(Boolean.TRUE.equals(request.getAttribute(ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER))) {
            return true;
        }

        ApikeyInfo apikey = BellaContext.getApikeyIgnoreNull();
        if(apikey == null) {
            return true;
        }

        String akCode = apikey.getCode();
        QpsCheckResult result = qpsLimiterManager.checkLimit(akCode, apikey.getQpsLimit());

        if(!result.isAllowed()) {
            response.setHeader("Retry-After", "1");
            throw new BellaException.RateLimitException(
                    String.format("QPS 超过限制（当前: %d, 限制: %d），请 1 秒后重试",
                            result.getCurrentQps(), result.getLimit()));
        }

        return true;
    }
}
