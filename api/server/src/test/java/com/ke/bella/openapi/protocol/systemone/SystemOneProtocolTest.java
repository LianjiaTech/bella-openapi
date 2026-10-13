package com.ke.bella.openapi.protocol.systemone;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemOneProtocolTest {
    @Test
    void preservesStructuredQuestionsAndAdditionalRequestFields() {
        SystemOneRequest request = JacksonUtils.deserialize("{"
                + "\"state\":\"payouts failing\","
                + "\"model\":\"jev-latest\","
                + "\"questions\":{\"is_urgent\":{\"type\":\"noul\","
                + "\"instructions\":{\"text\":\"Does this convey urgency?\"},"
                + "\"criteria\":{\"true\":[\"time-sensitive\"],\"false\":\"not urgent\"}}},"
                + "\"metadata\":[{\"source\":\"support\"}]"
                + "}", SystemOneRequest.class);

        Map<String, Object> body = JacksonUtils.deserialize(JacksonUtils.serialize(request), Map.class);
        assertEquals("payouts failing", body.get("state"));
        assertEquals("jev-latest", body.get("model"));
        assertTrue(!body.containsKey("cleared"));
        Map<?, ?> question = (Map<?, ?>) ((Map<?, ?>) body.get("questions")).get("is_urgent");
        assertEquals("Does this convey urgency?", ((Map<?, ?>) question.get("instructions")).get("text"));
        Map<?, ?> criteria = (Map<?, ?>) question.get("criteria");
        assertEquals(java.util.Arrays.asList("time-sensitive"), criteria.get("true"));
        assertEquals("not urgent", criteria.get("false"));
        assertEquals("support", ((Map<?, ?>) ((java.util.List<?>) body.get("metadata")).get(0)).get("source"));
    }

    @Test
    void preservesSuccessfulResponseFieldsAndUsage() {
        SystemOneResponse response = JacksonUtils.deserialize("{"
                + "\"model\":\"jev-1.13.0\","
                + "\"answers\":{\"is_urgent\":{\"type\":\"noul\",\"noul\":0.95}},"
                + "\"usage\":{\"input_tokens\":296,\"output_tokens\":20},"
                + "\"provider_field\":{\"confidence\":0.99}"
                + "}", SystemOneResponse.class);

        assertEquals("jev-1.13.0", response.getModel());
        assertEquals(0.95, ((Map<?, ?>) response.getAnswers().get("is_urgent")).get("noul"));
        assertEquals(316, response.getUsage().getTotalTokens());
        assertEquals(0.99, ((Map<?, ?>) response.getExtra().get("provider_field")).get("confidence"));
    }

    @Test
    void registersSystemOneEndpointAndProtocol() {
        AdaptorManager manager = AdaptorManager.getInstance();
        manager.register("/v1/systemone", new TypesafeSystemOneAdaptor());

        assertTrue(manager.support("/v1/systemone", "TypesafeSystemOneAdaptor"));
        assertEquals("/v*/systemone", EntityConstants.SystemBasicEndpoint.SYSTEM_ONE_ENDPOINT.getEndpoint());
        assertEquals(EntityConstants.SystemBasicCategory.CHAT, EntityConstants.SystemBasicEndpoint.SYSTEM_ONE_ENDPOINT.getCategory());
    }

    @Test
    void buildsJsonRequestWithBearerAuthentication() throws Exception {
        SystemOneRequest request = JacksonUtils.deserialize("{\"state\":\"hello\",\"model\":\"jev-latest\",\"questions\":{}}", SystemOneRequest.class);
        SystemOneProperty property = new SystemOneProperty();
        property.setAuth(new com.ke.bella.openapi.protocol.AuthorizationProperty(
                com.ke.bella.openapi.protocol.AuthorizationProperty.AuthType.BEARER, null, "secret", null));

        Request upstream = new TypesafeSystemOneAdaptor().buildRequest(request, "https://api.typesafe.ai/v1/systemone", property);

        assertEquals("Bearer secret", upstream.header("Authorization"));
        assertEquals("application/json", upstream.body().contentType().toString());
        Buffer buffer = new Buffer();
        upstream.body().writeTo(buffer);
        assertTrue(buffer.readUtf8().contains("\"questions\""));
    }

    @Test
    void usesProviderDeploymentNameWhenConfigured() throws Exception {
        SystemOneRequest request = JacksonUtils.deserialize("{\"model\":\"gateway-alias\"}", SystemOneRequest.class);
        SystemOneProperty property = new SystemOneProperty();
        property.setDeployName("jev-1.13.0");

        Request upstream = new TypesafeSystemOneAdaptor().buildRequest(request, "https://api.typesafe.ai/v1/systemone", property);

        Buffer buffer = new Buffer();
        upstream.body().writeTo(buffer);
        Map<?, ?> body = JacksonUtils.deserialize(buffer.readUtf8(), Map.class);
        assertEquals("jev-1.13.0", body.get("model"));
    }

    @Test
    void mapsUpstreamErrorStatusWithoutExposingCredentials() throws Exception {
        SystemOneResponse response = new SystemOneResponse();
        response.setExtraField("message", "invalid question");
        Response upstream = new Response.Builder()
                .request(new Request.Builder().url("https://api.typesafe.ai/v1/systemone").build())
                .protocol(Protocol.HTTP_1_1)
                .code(422)
                .message("Unprocessable Entity")
                .build();

        new TypesafeSystemOneAdaptor().errorCallback.callback(response, upstream);

        assertEquals(422, response.getError().getHttpCode());
        assertEquals("invalid question", response.getError().getMessage());
        assertTrue(!JacksonUtils.serialize(response).contains("secret"));
    }

    @Test
    void recordsUsageForLogging() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/systemone");
        processData.setRequestTime(0);
        processData.setResponse(JacksonUtils.deserialize("{\"usage\":{\"input_tokens\":4,\"output_tokens\":3}}", SystemOneResponse.class));

        new SystemOneLogHandler().process(processData);

        assertNotNull(processData.getUsage());
        assertEquals(7, ((SystemOneUsage) processData.getUsage()).getTotalTokens());
        assertEquals(7, processData.getMetrics().get("token"));
    }
}
