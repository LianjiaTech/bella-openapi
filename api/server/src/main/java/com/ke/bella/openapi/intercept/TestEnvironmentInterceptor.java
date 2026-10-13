package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class TestEnvironmentInterceptor extends HandlerInterceptorAdapter {
    @Value("${spring.profiles.active}")
    private String profile;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if("test".equals(profile)) {
            ApikeyInfo apikey = EndpointContext.getApikey();
            Operator op = BellaContext.getOperatorIgnoreNull();
            return apikey.getOwnerType().equals(EntityConstants.SYSTEM) || (op != null && StringUtils.isNotBlank(op.getManagerAk()));
        }
        return true;
    }
}
