package com.ke.bella.openapi.protocol.alpha.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class AlphaSearchAdaptor implements AlphaSearchProtocolAdaptor {
    public static final String ENDPOINT = AlphaSearchProtocolAdaptor.ENDPOINT;

    @Override
    public String endpoint() { return ENDPOINT; }

    @Override
    public String getDescription() { return "Alpha Search 原生协议"; }

    @Override
    public Class<?> getPropertyClass() { return AlphaSearchProperty.class; }

    public AlphaSearchResponse search(AlphaSearchRequest request, AlphaSearchExecutionContext context) {
        if(request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if(context == null) {
            throw new IllegalArgumentException("execution context is required");
        }
        AlphaSearchProperty property = context.getProperty(AlphaSearchProperty.class);
        String deployName = StringUtils.defaultIfBlank(context.getDeployName(),
                property == null ? null : property.getDeployName());
        byte[] forwardedBody = request.bodyWithModel(deployName);
        Request.Builder builder = buildRequest(context.getForwardUrl(), property, forwardedBody);
        try (Response response = HttpUtils.httpRequest(builder.build())) {
            byte[] responseBody = response.body() == null ? new byte[0] : response.body().bytes();
            if(!response.isSuccessful()) {
                throw toChannelException(response, responseBody);
            }
            return new AlphaSearchResponse(response.code(), response.header("Content-Type"), responseBody,
                    new AlphaSearchUsage(1));
        } catch (IOException e) {
            throw AlphaSearchException.forwarding(e.getMessage());
        }
    }

    Request.Builder buildRequest(String url, AlphaSearchProperty property, byte[] body) {
        if(StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("forwardUrl is required");
        }
        Request.Builder builder = authorizationBuilder(property == null ? null : property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.get("application/json"), body));
        builder.header("Accept", "application/json")
                .header("OpenAI-Beta", "responses=experimental")
                .header("originator", "codex_cli_rs");
        if(property != null && property.getHeaders() != null) {
            for(Map.Entry<String, String> header : property.getHeaders().entrySet()) {
                if(StringUtils.isNotBlank(header.getKey()) && header.getValue() != null
                        && !"authorization".equalsIgnoreCase(header.getKey())) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
        }
        return builder;
    }

    private Request.Builder authorizationBuilder(com.ke.bella.openapi.protocol.AuthorizationProperty auth) {
        Request.Builder builder = new Request.Builder();
        if(auth == null || StringUtils.isBlank(auth.getApiKey())) return builder;
        JsonNode oauth = parseIfObject(auth.getApiKey());
        if(oauth != null) {
            JsonNode accessToken = oauth.get("access_token");
            JsonNode accountId = oauth.get("account_id");
            if(!isNonBlankText(accessToken) || !isNonBlankText(accountId)) {
                throw new IllegalArgumentException("Alpha Search OAuth auth requires access_token and account_id");
            }
            builder.header("Authorization", "Bearer " + accessToken.asText())
                    .header("chatgpt-account-id", accountId.asText());
            return builder;
        }
        return authorizationRequestBuilder(auth);
    }

    private JsonNode parseIfObject(String value) {
        try {
            JsonNode node = JacksonUtils.MAPPER.readTree(value);
            return node != null && node.isObject() ? node : null;
        } catch (IOException | RuntimeException e) {
            if(StringUtils.trimToEmpty(value).startsWith("{")) {
                throw new IllegalArgumentException("Alpha Search OAuth auth must be valid JSON", e);
            }
            return null;
        }
    }

    private boolean isNonBlankText(JsonNode node) {
        return node != null && node.isTextual() && StringUtils.isNotBlank(node.asText());
    }

    private AlphaSearchException toChannelException(Response response, byte[] responseBody) {
        String message = StringUtils.defaultIfBlank(response.message(), "Alpha Search upstream request failed");
        String type = "Channel Exception";
        OpenapiResponse.OpenapiError error = null;
        String body = new String(responseBody, StandardCharsets.UTF_8);
        if(StringUtils.isNotBlank(body)) {
            try {
                JsonNode root = JacksonUtils.MAPPER.readTree(body);
                JsonNode upstreamError = root != null && root.has("error") ? root.get("error") : root;
                if(upstreamError != null && upstreamError.isObject()) {
                    type = StringUtils.defaultIfBlank(text(upstreamError, "type"), type);
                    message = StringUtils.defaultIfBlank(text(upstreamError, "message"), body);
                    error = OpenapiResponse.OpenapiError.builder()
                            .code(text(upstreamError, "code"))
                            .type(type)
                            .message(message)
                            .param(text(upstreamError, "param"))
                            .httpCode(response.code())
                            .build();
                } else {
                    message = body;
                }
            } catch (IOException | RuntimeException ignored) {
                message = body;
            }
        }
        return AlphaSearchException.provider(response.code(), type, message, error);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isValueNode() ? value.asText() : null;
    }

}
