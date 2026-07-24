package com.ke.bella.openapi.login.oauth.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.login.oauth.AbstractOAuthService;
import com.ke.bella.openapi.login.oauth.OAuthProperties;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class GoogleOAuthService extends AbstractOAuthService {

    public GoogleOAuthService(OAuthProperties properties) {
        super(properties.getRedirect(), properties.getProviders().get("google"));
    }

    @Override
    public String getProviderType() {
        return "google";
    }

    @Override
    protected String parseAccessToken(String response) throws IOException {
        JsonNode node = JacksonUtils.deserialize(response);
        if(node == null || !node.has("access_token")) {
            throw new IOException("Invalid token response");
        }
        return node.get("access_token").asText();
    }

    @Override
    protected Operator getUserInfo(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url(config.getUserInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if(!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to get user info");
            }
            JsonNode userInfo = JacksonUtils.deserialize(response.body().string());
            if(userInfo == null) {
                throw new IOException("Invalid userInfo response");
            }
            Operator operator = new Operator();
            operator.setSourceId(userInfo.get("sub").asText());
            operator.setSource("google");
            operator.setUserName(userInfo.get("name").asText());
            operator.setEmail(userInfo.get("email").asText());
            operator.setUserId(-1L);
            return operator;
        }
    }
}
