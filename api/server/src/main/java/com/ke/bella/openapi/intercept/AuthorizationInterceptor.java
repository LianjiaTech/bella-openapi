package com.ke.bella.openapi.intercept;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.CredentialExtractor;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.service.AuthService;

@Component
public class AuthorizationInterceptor extends com.ke.bella.openapi.server.intercept.AuthorizationInterceptor {
    @Autowired
    private ApikeyService apikeyService;
    @Autowired
    private AuthService authService;

    @Value("${bella.console.user-quota-edit-enabled:false}")
    private boolean userQuotaEditEnabled;

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
            op.getOptionalInfo().put("roleCode", apikeyInfo.getRoleCode());
            op.getOptionalInfo().put("userQuotaEditEnabled", userQuotaEditEnabled);
            EndpointContext.setAuthResult(AuthResult.builder()
                    .apikeyInfo(apikeyInfo)
                    .credentialType(CredentialType.API_KEY)
                    .build());
            hasPermission = apikeyInfo.hasPermission(url);
        } else {
            String auth = CredentialExtractor.extract(request);
            if(StringUtils.isEmpty(auth)) {
                throw new BellaException.AuthorizationException("Authorization is empty");
            }
            AuthResult authResult = authService.authenticate(auth);
            ApikeyInfo apikeyInfo = authResult.getApikeyInfo();
            hasPermission = apikeyInfo.hasPermission(url);
            if(apikeyInfo.hasAllocatedPermission()) {
                String userAkCode = request.getHeader(BellaContext.BELLA_USER_AK_HEADER);
                if(StringUtils.isNotEmpty(userAkCode)) {
                    ApikeyInfo userAkInfo = apikeyService.verifyAuthByCode(userAkCode);
                    userAkInfo.setApikey(apikeyInfo.getApikey());
                    apikeyInfo = userAkInfo;
                    authResult.setApikeyInfo(apikeyInfo);
                }
            }
            EndpointContext.setAuthResult(authResult);
        }
        if(!hasPermission) {
            if(CredentialType.CLIENT_SECRET == EndpointContext.getProcessData().getCredentialType()) {
                throw new BellaException.ClientSecretException("client_secret_forbidden", org.springframework.http.HttpStatus.FORBIDDEN,
                        "Client secret is not allowed to access this endpoint");
            }
            throw new BellaException.AuthorizationException("没有操作权限");
        }
        return true;
    }
}
