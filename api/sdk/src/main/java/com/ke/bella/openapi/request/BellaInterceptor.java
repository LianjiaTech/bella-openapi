package com.ke.bella.openapi.request;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.NotNull;

import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;

import lombok.NoArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@NoArgsConstructor
public class BellaInterceptor implements Interceptor {

    private String openapiHost;

    //在异步线程中使用时需要传入context
    private Map<String, Object> context;

    public BellaInterceptor(String openapiHost, Map<String, Object> context) {
        this.openapiHost = stripProtocol(openapiHost);
        this.context = context;
    }

    private String stripProtocol(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith("https://")) {
            return url.substring(8);
        }
        if (url.startsWith("http://")) {
            return url.substring(7);
        }
        return url;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request originalRequest = chain.request();
        if(!originalRequest.url().host().equals(openapiHost)) {
            return chain.proceed(originalRequest);
        }
        Map<String, Object> context = this.context;
        Map<String, String> headers = (Map<String, String>) Optional.ofNullable(context.get("headers")).orElse(new HashMap<>());
        Request.Builder bellaRequest = originalRequest.newBuilder();
        if(MapUtils.isNotEmpty(headers)) {
            headers.forEach(bellaRequest::header);
        }
        Operator op = (Operator) Optional.ofNullable(context.get("oper")).orElse(new Operator());
        String user = op.getUserId() == null ? op.getSourceId() : op.getUserId().toString();
        ApikeyInfo apikeyInfo = (ApikeyInfo) Optional.ofNullable(context.get("ak")).orElse(new ApikeyInfo());
        if(originalRequest.header("Authorization") == null && apikeyInfo.getApikey() != null) {
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
}
