package com.ke.bella.openapi.request;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.auth.AuthResult;

import lombok.NoArgsConstructor;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@NoArgsConstructor
public class BellaInterceptor implements Interceptor {

    private static volatile String configuredServiceClientCode;
    private static volatile String configuredServiceClientToken;

    private String openapiHost;
    private int openapiPort = -1;

    // 在异步线程中使用时需要传入context
    private Map<String, Object> context;

    public BellaInterceptor(String openapiHost, Map<String, Object> context) {
        boolean hasProtocol = StringUtils.contains(openapiHost, "://");
        HttpUrl parsed = openapiHost == null ? null
                : hasProtocol ? HttpUrl.parse(openapiHost) : HttpUrl.parse("http://" + openapiHost);
        if(parsed != null) {
            this.openapiHost = parsed.host();
            this.openapiPort = hasProtocol ? parsed.port() : -1;
        } else {
            this.openapiHost = stripProtocol(openapiHost);
        }
        this.context = context;
    }

    public static void configureServiceClient(String code, String token) {
        configuredServiceClientCode = StringUtils.trimToNull(code);
        configuredServiceClientToken = StringUtils.trimToNull(token);
    }

    public static void addServiceClientHeaders(Request.Builder request, Map<String, Object> context) {
        if(context == null) {
            return;
        }
        AuthResult authResult = (AuthResult) context.get("authResult");
        if(authResult != null && authResult.isServiceClient()) {
            ApikeyInfo apikeyInfo = (ApikeyInfo) context.get("ak");
            applyServiceClientHeaders(request, apikeyInfo == null ? authResult.getApikeyInfo() : apikeyInfo);
        }
    }

    private String stripProtocol(String url) {
        if(url == null) {
            return null;
        }
        if(url.startsWith("https://")) {
            return url.substring(8);
        }
        if(url.startsWith("http://")) {
            return url.substring(7);
        }
        return url;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request originalRequest = chain.request();
        Map<String, Object> context = this.context == null ? new HashMap<>() : this.context;
        Map<String, String> headers = (Map<String, String>) Optional.ofNullable(context.get("headers")).orElse(new HashMap<>());
        Request.Builder bellaRequest = originalRequest.newBuilder();
        AuthResult authResult = (AuthResult) context.get("authResult");
        ApikeyInfo apikeyInfo = (ApikeyInfo) context.get("ak");
        if(apikeyInfo == null && authResult != null) {
            apikeyInfo = authResult.getApikeyInfo();
        }
        if(apikeyInfo == null) {
            apikeyInfo = new ApikeyInfo();
        }
        boolean trustedServiceClientRequest = isTrustedServiceClientRequest(originalRequest, authResult, apikeyInfo);
        if(!trustedServiceClientRequest) {
            bellaRequest.removeHeader(BellaContext.SERVICE_CLIENT_HEADER);
            bellaRequest.removeHeader(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER);
        }
        if(!isOpenapiHost(originalRequest.url())) {
            if(!trustedServiceClientRequest && authResult != null && authResult.isServiceClient()
                    && isConfiguredServiceClientAuthorization(originalRequest.header("Authorization"))) {
                bellaRequest.removeHeader("Authorization");
            }
            return chain.proceed(bellaRequest.build());
        }
        if(MapUtils.isNotEmpty(headers)) {
            headers.forEach(bellaRequest::header);
        }
        Operator op = (Operator) Optional.ofNullable(context.get("oper")).orElse(new Operator());
        String user = op.getUserId() == null ? op.getSourceId() : op.getUserId().toString();
        if(authResult != null && authResult.isServiceClient()) {
            applyServiceClientHeaders(bellaRequest, apikeyInfo);
        } else if(originalRequest.header("Authorization") == null && apikeyInfo.getApikey() != null) {
            bellaRequest.header("Authorization", "Bearer " + apikeyInfo.getApikey());
        }
        if(user == null) {
            user = apikeyInfo.getOwnerCode();
        }
        if(user != null) {
            bellaRequest.header("ucid", user);
        }
        return chain.proceed(bellaRequest.build());
    }

    private boolean isOpenapiHost(HttpUrl url) {
        return url.host().equals(openapiHost) && (openapiPort < 0 || url.port() == openapiPort);
    }

    private static void applyServiceClientHeaders(Request.Builder request, ApikeyInfo apikeyInfo) {
        if(StringUtils.isNotBlank(configuredServiceClientCode)
                && StringUtils.isNotBlank(configuredServiceClientToken)
                && apikeyInfo != null && StringUtils.isNotBlank(apikeyInfo.getCode())) {
            request.removeHeader("Authorization");
            request.removeHeader(BellaContext.SERVICE_CLIENT_HEADER);
            request.removeHeader(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER);
            request.header("Authorization", "Bearer " + configuredServiceClientToken);
            request.header(BellaContext.SERVICE_CLIENT_HEADER, configuredServiceClientCode);
            request.header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER, apikeyInfo.getCode());
        }
    }

    private static boolean isConfiguredServiceClientAuthorization(String authorization) {
        return StringUtils.isNotBlank(configuredServiceClientToken)
                && StringUtils.equals(authorization, "Bearer " + configuredServiceClientToken);
    }

    private static boolean isTrustedServiceClientRequest(Request request, AuthResult authResult,
            ApikeyInfo apikeyInfo) {
        return authResult != null && authResult.isServiceClient()
                && isConfiguredServiceClientAuthorization(request.header("Authorization"))
                && StringUtils.equals(configuredServiceClientCode, request.header(BellaContext.SERVICE_CLIENT_HEADER))
                && StringUtils.isNotBlank(request.header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER))
                && StringUtils.equals(apikeyInfo.getCode(), request.header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER));
    }

}
