package com.ke.bella.openapi.login.oauth;

import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

public abstract class AbstractOAuthService implements OAuthService {
    protected final String redirect;
    protected final OAuthProperties.ProviderConfig config;
    protected final OkHttpClient client;

    protected AbstractOAuthService(String redirect, OAuthProperties.ProviderConfig config) {
        this.redirect = redirect;
        this.config = config;
        this.client = HttpUtils.defaultOkhttpClient();
    }

    @Override
    public String getAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(config.getAuthUri())
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", getRedirectUrl())
                .queryParam("response_type", "code")
                .queryParam("scope", config.getScope())
                .queryParam("state", state)
                .build()
                .toString();
    }

    @Override
    public Operator handleCallback(String code, String state) throws IOException {
        // 1. 获取 access token
        String accessToken = getAccessToken(code);
        // 2. 获取用户信息
        return getUserInfo(accessToken);
    }

    protected String getAccessToken(String code) throws IOException {
        FormBody formBody = new FormBody.Builder()
                .add("client_id", config.getClientId())
                .add("client_secret", config.getClientSecret())
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", getRedirectUrl())
                .build();

        Request request = new Request.Builder()
                .url(config.getTokenUri())
                .post(formBody)
                .header("Accept", "application/json")  // 直接要求返回 JSON
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to get access token");
            }
            return parseAccessToken(response.body().string());
        }
    }

    protected String getRedirectUrl() {
        return redirect + "/openapi/oauth/callback/" + getProviderType();
    }

    protected abstract String parseAccessToken(String response) throws IOException;

    protected abstract Operator getUserInfo(String accessToken) throws IOException;
}
