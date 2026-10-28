package com.ke.bella.openapi.intercept;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

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
        String authorizationHeader = singleHeader(request, "Authorization");
        Operator op = BellaContext.getOperatorIgnoreNull();
        if(op != null && (CredentialExtractor.isServiceClient(CredentialExtractor.extract(request))
                || request.getHeader(BellaContext.SERVICE_CLIENT_HEADER) != null
                || request.getHeader(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER) != null)) {
            throw new BellaException.AuthorizationException("ServiceClient credentials cannot be combined with operator identity");
        }
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
            String serviceClientCode = singleHeader(request, BellaContext.SERVICE_CLIENT_HEADER);
            String effectiveAkCode = singleHeader(request, BellaContext.EFFECTIVE_API_KEY_CODE_HEADER);
            String legacyUserAkCode = singleHeader(request, BellaContext.BELLA_USER_AK_HEADER);
            boolean hasServiceClientHeader = serviceClientCode != null;
            boolean legacyServiceClient = CredentialExtractor.isServiceClient(auth);
            boolean legacyServiceClientRequest = legacyServiceClient && effectiveAkCode != null
                    && !hasServiceClientHeader;
            boolean explicitServiceClient = hasServiceClientHeader
                    || (effectiveAkCode != null && !legacyServiceClientRequest);
            boolean bearerAuthorization = StringUtils.startsWith(authorizationHeader, "Bearer ");
            if(explicitServiceClient) {
                if(!hasServiceClientHeader || StringUtils.isBlank(effectiveAkCode)
                        || !bearerAuthorization || StringUtils.isBlank(auth)) {
                    throw new BellaException.AuthorizationException("ServiceClient credentials require Authorization, X-Service-Client and X-Effective-Api-Key-Code");
                }
                if(legacyUserAkCode != null) {
                    throw new BellaException.AuthorizationException("X-BELLA-USER-AK-CODE cannot be combined with ServiceClient credentials");
                }
            } else if(legacyServiceClientRequest && !bearerAuthorization) {
                throw new BellaException.AuthorizationException("Legacy ServiceClient credentials require Authorization: Bearer");
            } else if(legacyServiceClient && legacyUserAkCode != null) {
                throw new BellaException.AuthorizationException("X-BELLA-USER-AK-CODE cannot be combined with ServiceClient credentials");
            }
            if(StringUtils.isEmpty(auth)) {
                throw new BellaException.AuthorizationException("Authorization is empty");
            }
            AuthResult authResult = (explicitServiceClient || legacyServiceClient)
                    ? authService.authenticateWithEffectiveApiKeyCode(auth, effectiveAkCode, serviceClientCode)
                    : authService.authenticate(auth);
            ApikeyInfo apikeyInfo = authResult.getApikeyInfo();
            if(!authResult.isServiceClient() && apikeyInfo.hasAllocatedPermission() && legacyUserAkCode != null) {
                ApikeyInfo userAkInfo = apikeyService.verifyAuthByCode(legacyUserAkCode);
                userAkInfo.setApikey(apikeyInfo.getApikey());
                apikeyInfo = userAkInfo;
                authResult.setApikeyInfo(apikeyInfo);
            }
            hasPermission = apikeyInfo.hasPermission(url);
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

    private String singleHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if(values == null) {
            return null;
        }
        List<String> headers = Collections.list(values);
        if(headers.isEmpty()) {
            return null;
        }
        if(headers.size() != 1 || StringUtils.isBlank(headers.get(0)) || !headers.get(0).equals(headers.get(0).trim())) {
            throw new BellaException.AuthorizationException("Invalid " + name + " header");
        }
        return headers.get(0);
    }
}
