package com.ke.bella.openapi.intercept;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.service.ApikeyService;

@Component
public class AuthorizationInterceptor extends com.ke.bella.openapi.server.intercept.AuthorizationInterceptor {
    @Autowired
    private ApikeyService apikeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if(Boolean.TRUE.equals(request.getAttribute(ASYNC_REQUEST_MARKER))) {
            return true;
        }
        boolean hasPermission;
        String url = request.getRequestURI();
        Operator op = BellaContext.getOperatorIgnoreNull();
        if(op != null) {
            String apikey = op.getManagerAk();
            ApikeyInfo apikeyInfo = apikeyService.verifyAuth(apikey);
            if(apikeyInfo == null) {
                throw new ChannelException.AuthorizationException("apikey不存在");
            }
            op.getOptionalInfo().put("roles", apikeyInfo.getRolePath().getIncluded());
            op.getOptionalInfo().put("excludes", apikeyInfo.getRolePath().getExcluded());
            EndpointContext.setApikey(apikeyInfo);
            hasPermission = apikeyInfo.hasPermission(url);
        } else {
            String auth = request.getHeader(getHeader(request.getRequestURI()));
            if(StringUtils.isEmpty(auth)) {
                throw new ChannelException.AuthorizationException("Authorization is empty");
            }
            ApikeyInfo apikeyInfo = apikeyService.verifyAuth(auth);
            hasPermission = apikeyInfo.hasPermission(url);
            if(apikeyInfo.hasAllocatedPermission()) {
                String userAkCode = request.getHeader(BellaContext.BELLA_USER_AK_HEADER);
                if(StringUtils.isNotEmpty(userAkCode)) {
                    ApikeyInfo userAkInfo = apikeyService.queryByCode(userAkCode, true);
                    userAkInfo.setApikey(auth);
                    apikeyInfo = userAkInfo;
                }
            }
            EndpointContext.setApikey(apikeyInfo);
        }
        if(!hasPermission) {
            throw new ChannelException.AuthorizationException("没有操作权限");
        }
        return true;
    }

    private String getHeader(String uri) {
        return HttpHeaders.AUTHORIZATION;
    }
}
