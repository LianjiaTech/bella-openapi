package com.ke.bella.openapi.server.intercept;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

public class AuthorizationInterceptor extends HandlerInterceptorAdapter {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if(Boolean.TRUE.equals(request.getAttribute(ASYNC_REQUEST_MARKER))) {
            return true;
        }
        ApikeyInfo apikeyInfo = BellaContext.getApikeyIgnoreNull();
        if(apikeyInfo == null) {
            throw new BellaException.AuthorizationException("invalid Authorization header");
        }

        String user = request.getHeader("ucid");
        if(user != null) {
            BellaContext.setOperator(Operator.builder().userId(0L).sourceId(user).build());
        }

        return true;
    }
}
