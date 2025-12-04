package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.limiter.QpsLimiterManager;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
            // APIKey 未认证，由 AuthorizationInterceptor 处理
            return true;
        }

        // 获取 QPS 配置
        Integer qpsLimit = apikey.getQpsLimit();
        if (qpsLimit == null || qpsLimit <= 0) {
            // 未配置 QPS 限制，放行
			return true;
        }

        // 执行 QPS 限流检查
        String akCode = apikey.getCode();
        boolean allowed = qpsLimiterManager.checkLimit(akCode, qpsLimit);

        if (!allowed) {
            // 超过 QPS 限制，拒绝请求
            String errorMsg = String.format("QPS 超过限制: %d 请求/秒，请稍后重试", qpsLimit);
            log.warn("QPS limit exceeded - akCode: {}, limit: {}, url: {}",
                     akCode, qpsLimit, request.getRequestURI());
            throw new ChannelException.RateLimitException(errorMsg);
        }

        return true;
    }
}
