package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.utils.DateTimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

@Component
public class MonthQuotaInterceptor extends HandlerInterceptorAdapter {
    @Autowired
    private ApikeyService apikeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if(Boolean.TRUE.equals(request.getAttribute(ASYNC_REQUEST_MARKER))) {
            return true;
        }
        ApikeyInfo apikey = EndpointContext.getApikey();
        // 非子ak 或 已指定额度的子ak
        if(apikey.getParentInfo() == null || apikey.getMonthQuota().doubleValue() > 0) {
            BigDecimal cost = apikeyService.loadCost(apikey.getCode(), DateTimeUtils.getCurrentMonth());
            double costVal = cost.doubleValue() / 100.0;
            if(apikey.getMonthQuota().doubleValue() <= costVal) {
                String msg = "已达每月额度上限, limit:" + apikey.getMonthQuota() + ", cost:" + costVal;
                throw new BellaException.RateLimitException(msg);
            }
        }
        // 父ak的总额度不能超出
        if(apikey.getParentInfo() != null) {
            BigDecimal quota = apikey.getParentInfo().getMonthQuota();
            BigDecimal cost = apikeyService.loadCost(apikey.getParentCode(), DateTimeUtils.getCurrentMonth());
            double costVal = cost.doubleValue() / 100.0;
            if(quota.doubleValue() <= costVal) {
                String msg = "主ak的总额度已达上限, limit:" + quota + ", cost:" + costVal;
                throw new BellaException.RateLimitException(msg);
            }
        }
        return true;
    }
}
