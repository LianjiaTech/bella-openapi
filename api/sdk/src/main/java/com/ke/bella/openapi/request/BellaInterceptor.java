package com.ke.bella.openapi.request;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@NoArgsConstructor
public class BellaInterceptor implements Interceptor {
    //在异步线程中使用时需要传入context
    private Map<String, Object> context;

    @NotNull
    @SuppressWarnings("unchecked")
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Map<String, Object> context = this.context;
        if(context == null) {
            context = BellaContext.snapshot();
        }
        Map<String, String> headers = (Map<String, String>) Optional.ofNullable(context.get("headers")).orElse(new HashMap<>());
        Request originalRequest = chain.request();
        Request.Builder bellaRequest = originalRequest.newBuilder();
        if(MapUtils.isNotEmpty(headers)) {
            headers.forEach(bellaRequest::header);
        }
        Operator op = (Operator) Optional.ofNullable(context.get("oper")).orElse(new Operator());
        String user = op.getUserId() == null ? op.getSourceId() : op.getUserId().toString();
        ApikeyInfo apikeyInfo = (ApikeyInfo) Optional.ofNullable(context.get("ak")).orElse(new ApikeyInfo());
        if(apikeyInfo.getApikey() != null) {
            bellaRequest.header("Authorization", "Bearer " + apikeyInfo.getApikey());
        }
        if(user == null) {
            user = apikeyInfo.getOwnerCode();
        }
        if(user != null) {
            bellaRequest.header("ucid", op.getSourceId());
        }
         return chain.proceed(bellaRequest.build());
    }
}
