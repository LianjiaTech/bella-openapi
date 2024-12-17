package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.utils.DateTimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;

import static com.ke.bella.openapi.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

@Component
public class MonthQuotaInterceptor extends HandlerInterceptorAdapter {
    @Autowired
    private ApikeyService apikeyService;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (Boolean.TRUE.equals(request.getAttribute(ASYNC_REQUEST_MARKER))) {
            return true;
        }
        ApikeyInfo apikey = EndpointContext.getApikey();
        BigDecimal cost = apikeyService.loadCost(apikey.getCode(), DateTimeUtils.getCurrentMonth());
        double costVal = cost.doubleValue() / 100.0;
        if(apikey.getMonthQuota().doubleValue() <= costVal) {
            String msg = "已达每月额度上限, limit:" + apikey.getMonthQuota() + ", cost:" + costVal;
            throw new ChannelException.RateLimitException(msg);
        }
        return true;
    }
}
