package com.ke.bella.openapi.protocol.alpha.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.AuthorizationProperty.AuthType;
import com.sun.net.httpserver.HttpServer;
import okhttp3.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlphaSearchAdaptorTest {

    private final AlphaSearchAdaptor adaptor = new AlphaSearchAdaptor();
    private HttpServer server;

    @BeforeEach
    void setUp() {
        EndpointContext.clearAll();
    }

    @AfterEach
    void tearDown() {
        if(server != null) {
            server.stop(0);
        }
        EndpointContext.clearAll();
    }

    @Test
    void buildRequestAddsAlphaSearchHeadersAndOAuthHeaders() throws IOException {
        AuthorizationProperty auth = new AuthorizationProperty();
        auth.setType(AuthType.BEARER);
        auth.setApiKey("{\"access_token\":\"oauth-token\",\"account_id\":\"account-123\"}");
        AlphaSearchProperty property = new AlphaSearchProperty();
        property.setAuth(auth);

        Request request = adaptor.buildRequest("https://chatgpt.com/backend-api/codex/alpha/search", property,
                "{\"model\":\"gpt-5\",\"commands\":[{\"type\":\"search_query\"}]}".getBytes()).build();

        assertEquals("Bearer oauth-token", request.header("Authorization"));
        assertEquals("account-123", request.header("chatgpt-account-id"));
        assertEquals("application/json", request.body().contentType().toString());
        assertEquals("application/json", request.header("Accept"));
        assertEquals("responses=experimental", request.header("OpenAI-Beta"));
        assertEquals("codex_cli_rs", request.header("originator"));
    }

    @Test
    void buildRequestRejectsIncompleteOAuthJson() {
        AuthorizationProperty auth = new AuthorizationProperty();
        auth.setType(AuthType.BEARER);
        auth.setApiKey("{\"access_token\":\"oauth-token\"}");
        AlphaSearchProperty property = new AlphaSearchProperty();
        property.setAuth(auth);

        assertThrows(IllegalArgumentException.class, () -> adaptor.buildRequest("https://example.com/search", property,
                "{}".getBytes()));
    }

    @Test
    void buildRequestRejectsMalformedOAuthJson() {
        AuthorizationProperty auth = new AuthorizationProperty();
        auth.setType(AuthType.BEARER);
        auth.setApiKey("{malformed");
        AlphaSearchProperty property = new AlphaSearchProperty();
        property.setAuth(auth);

        assertThrows(IllegalArgumentException.class, () -> adaptor.buildRequest("https://example.com/search", property,
                "{}".getBytes()));
    }

    @Test
    void searchForwardsRequestAndPreservesCodexSearchResponse() throws Exception {
        byte[] expectedResponse = fixture();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> openaiBeta = new AtomicReference<>();
        AtomicReference<String> originator = new AtomicReference<>();
        AtomicReference<byte[]> receivedBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/backend-api/codex/alpha/search", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            openaiBeta.set(exchange.getRequestHeaders().getFirst("OpenAI-Beta"));
            originator.set(exchange.getRequestHeaders().getFirst("originator"));
            receivedBody.set(readBytes(exchange.getRequestBody()));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, expectedResponse.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(expectedResponse);
            }
        });
        server.start();

        byte[] requestBody = ("{"
                + "\"id\":\"search-test\","
                + "\"model\":\"gpt-5-codex\","
                + "\"reasoning\":{\"effort\":\"medium\"},"
                + "\"input\":\"latest OpenAI Codex\","
                + "\"commands\":[{\"type\":\"search_query\",\"query\":\"latest OpenAI Codex\"}],"
                + "\"settings\":{\"response_length\":\"short\"},"
                + "\"country\":\"CN\",\"region\":\"Shanghai\",\"city\":\"Shanghai\","
                + "\"timezone\":\"Asia/Shanghai\"}").getBytes(StandardCharsets.UTF_8);
        AuthorizationProperty auth = new AuthorizationProperty();
        auth.setType(AuthType.BEARER);
        auth.setApiKey("test-key");
        AlphaSearchProperty property = new AlphaSearchProperty();
        property.setAuth(auth);

        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/backend-api/codex/alpha/search";
        AlphaSearchRequest request = AlphaSearchRequest.parse(requestBody);
        AlphaSearchResponse result = adaptor.search(request,
                new AlphaSearchExecutionContext(url, null, property));

        assertEquals(200, result.getStatus());
        assertEquals("application/json", result.getContentType());
        assertArrayEquals(expectedResponse, result.getBody());
        JsonNode response = com.ke.bella.openapi.utils.JacksonUtils.MAPPER.readTree(result.getBody());
        assertNotNull(response.get("encrypted_output"));
        assertNotNull(response.get("output"));
        assertEquals(1, response.get("results").size());

        assertEquals("POST", method.get());
        assertEquals("/backend-api/codex/alpha/search", path.get());
        assertEquals("Bearer test-key", authorization.get());
        assertEquals("responses=experimental", openaiBeta.get());
        assertEquals("codex_cli_rs", originator.get());
        assertArrayEquals(requestBody, receivedBody.get());
        JsonNode forwardedRequest = com.ke.bella.openapi.utils.JacksonUtils.MAPPER.readTree(receivedBody.get());
        assertEquals("gpt-5-codex", forwardedRequest.get("model").asText());
        assertEquals("search_query", forwardedRequest.get("commands").get(0).get("type").asText());
        assertEquals(1, result.getUsage().getRequestCount());
    }

    @Test
    void searchUsesExplicitDeployNameWithoutEndpointContext() throws Exception {
        AtomicReference<byte[]> receivedBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            receivedBody.set(readBytes(exchange.getRequestBody()));
            byte[] response = "{\"results\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        byte[] requestBody = "{\"model\":\"client-model\",\"commands\":{\"search_query\":[]}}"
                .getBytes(StandardCharsets.UTF_8);
        AlphaSearchProperty property = new AlphaSearchProperty();
        AlphaSearchRequest request = AlphaSearchRequest.parse(requestBody);
        AlphaSearchResponse response = adaptor.search(request,
                new AlphaSearchExecutionContext("http://127.0.0.1:" + server.getAddress().getPort() + "/search",
                        "provider-model", property));

        assertEquals(200, response.getStatus());
        JsonNode forwardedRequest = com.ke.bella.openapi.utils.JacksonUtils.MAPPER.readTree(receivedBody.get());
        assertEquals("provider-model", forwardedRequest.get("model").asText());
    }

    private byte[] fixture() {
        return ("{"
                + "\"encrypted_output\":\"encrypted-output\","
                + "\"output\":\"search result\","
                + "\"results\":[{"
                + "\"type\":\"text_result\","
                + "\"title\":\"Example result\","
                + "\"url\":\"https://example.com\","
                + "\"snippet\":\"Example snippet\""
                + "}]}" ).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] readBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
