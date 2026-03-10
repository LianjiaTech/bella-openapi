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
import com.ke.bella.openapi.common.exception.BellaException;
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
                throw new BellaException.AuthorizationException("apikey不存在");
            }
            op.getOptionalInfo().put("roles", apikeyInfo.getRolePath().getIncluded());
            op.getOptionalInfo().put("excludes", apikeyInfo.getRolePath().getExcluded());
            EndpointContext.setApikey(apikeyInfo);
            hasPermission = apikeyInfo.hasPermission(url);
        } else {
            // 优先检查标准 Authorization header
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);

            // 如果为空，检查协议特定的备选 header
            if(StringUtils.isEmpty(auth)) {
                String alternativeHeader = getAlternativeHeader(request.getRequestURI());
                if(alternativeHeader != null) {
                    auth = request.getHeader(alternativeHeader);
                }
                // 备选 header 也为空，抛出异常
                if (StringUtils.isEmpty(auth)) {
                    throw new BellaException.AuthorizationException("Authorization is empty");
                }
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
            throw new BellaException.AuthorizationException("没有操作权限");
        }
        return true;
    }

    /**
     * 根据请求 URI 获取协议特定的备选认证 header 名称。
     *
     * @param uri 请求的 URI
     * @return 备选 header 名称，若无备选则返回 null
     */
    private String getAlternativeHeader(String uri) {
        // Gemini API 使用 x-goog-api-key 作为认证 header
        if (uri.startsWith("/v1beta/models") || uri.startsWith("/v1beta1/publishers/google/models")) {
            return "x-goog-api-key";
        }

        // 其他协议无备选 header
        return null;
    }
}
