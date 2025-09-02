package com.ke.bella.openapi.login.oauth.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.login.oauth.AbstractOAuthService;
import com.ke.bella.openapi.login.oauth.OAuthProperties;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.HashMap;

public class GithubOAuthService extends AbstractOAuthService {

    public GithubOAuthService(OAuthProperties properties) {
        super(properties.getRedirect(), properties.getProviders().get("github"));
    }

    @Override
    public String getProviderType() {
        return "github";
    }

    @Override
    protected String parseAccessToken(String response) throws IOException {
        JsonNode node = JacksonUtils.deserialize(response);
        if (node == null || !node.has("access_token")) {
            throw new IOException("Invalid token response");
        }
        return node.get("access_token").asText();
    }

    @Override
    protected Operator getUserInfo(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(config.getUserInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to get user info");
            }
            JsonNode userInfo = JacksonUtils.deserialize(response.body().string());
            if (userInfo == null) {
                throw new IOException("Invalid userInfo response");
            }
            
            return Operator.builder()
                    .userId(-1L)
                    .userName(userInfo.get("login").asText())  // GitHub 用 login 作为用户名
                    .email(userInfo.has("email") ? userInfo.get("email").asText() : null)  // email 可能不公开
                    .source("github")
                    .sourceId(String.valueOf(userInfo.get("id").asLong()))
                    .optionalInfo(new HashMap<>())
                    .build();
        }
    }
}
