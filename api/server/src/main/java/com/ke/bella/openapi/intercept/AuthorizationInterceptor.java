package com.ke.bella.openapi.intercept;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.configuration.OpenApiProperties;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.utils.MatchUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

import static com.ke.bella.openapi.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

@Component
@Slf4j
public class AuthorizationInterceptor extends HandlerInterceptorAdapter {
    @Autowired
    private ApikeyService apikeyService;
    @Autowired
    private OpenApiProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if(Boolean.TRUE.equals(request.getAttribute(ASYNC_REQUEST_MARKER))) {
            return true;
        }
        boolean hasPermission;
        String url = request.getRequestURI();
        Operator op = BellaContext.getOperatorIgnoreNull();
        if(op != null) {
            List<String> roles = Lists.newArrayList(properties.getLoginRoles());
            List<String> excludes = Lists.newArrayList(properties.getLoginExcludes());
            if(properties.getManagers().containsKey(op.getUserId())) {
                String akCode = properties.getManagers().get(op.getUserId());
                ApikeyInfo apikeyInfo = apikeyService.queryByCode(akCode, true);
                if(apikeyInfo == null) {
                    throw new ChannelException.AuthorizationException("console apikey不存在");
                }
                op.getOptionalInfo().put("roles", apikeyInfo.getRolePath().getIncluded());
                op.getOptionalInfo().put("excludes", apikeyInfo.getRolePath().getExcluded());
                EndpointContext.setApikey(apikeyInfo);
                hasPermission = apikeyInfo.hasPermission(url);
            } else {
                op.getOptionalInfo().put("roles", roles);
                op.getOptionalInfo().put("excludes", excludes);
                hasPermission = roles.stream().anyMatch(role -> MatchUtils.matchUrl(role, url))
                        && excludes.stream().noneMatch(exclude -> MatchUtils.matchUrl(exclude, url));
            }
        } else {
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            if(StringUtils.isEmpty(auth)) {
                throw new ChannelException.AuthorizationException("Authorization is empty");
            }
            ApikeyInfo apikeyInfo = apikeyService.verifyAuthHeader(auth);
            EndpointContext.setApikey(apikeyInfo);
            hasPermission = apikeyInfo.hasPermission(url);
        }
        if(!hasPermission) {
            throw new ChannelException.AuthorizationException("没有操作权限");
        }
        return true;
    }
}
