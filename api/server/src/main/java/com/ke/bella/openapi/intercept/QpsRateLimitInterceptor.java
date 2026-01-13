package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.limiter.QpsLimiterManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

/**
 * QPS 限流拦截器
 * 基于滑动窗口算法实现精确的 QPS 限流，在请求到达业务逻辑前进行拦截
 * 拦截器顺序: AuthorizationInterceptor -> QpsRateLimitInterceptor -> MonthQuotaInterceptor
 */
@Component
public class QpsRateLimitInterceptor extends HandlerInterceptorAdapter {

    @Autowired
    private QpsLimiterManager qpsLimiterManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跳过异步请求标记（避免重复处理）
        if (Boolean.TRUE.equals(request.getAttribute(ASYNC_REQUEST_MARKER))) {
            return true;
        }

        // 获取当前 APIKey 信息
        ApikeyInfo apikey = EndpointContext.getApikey();
        if (apikey == null) {
            return true;
        }

        // 执行 QPS 限流检查（Manager 内部处理默认值和开关逻辑）
        String akCode = apikey.getCode();
        boolean allowed = qpsLimiterManager.checkLimit(akCode, apikey.getQpsLimit());

        if (!allowed) {
            throw new ChannelException.RateLimitException("QPS 超过限制，请稍后重试");
        }

        return true;
    }
}
