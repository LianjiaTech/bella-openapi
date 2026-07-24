package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.ke.bella.openapi.protocol.asr.HuoshanProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HuoshanBigModelFlashAdaptorTest {
    private final HuoshanBigModelFlashAsr adaptor = new HuoshanBigModelFlashAsr();

    @Test
    public void componentClassNameIsProtocolName() {
        assertEquals("HuoshanBigModelFlashAsr", adaptor.getClass().getSimpleName());
        assertEquals("/v1/audio/asr/flash", adaptor.endpoint());
    }

    @Test
    public void latestConsoleAuthBuildsHttpFlashRequestAndConvertsUtterances() throws Exception {
        CaptureHttpServer server = CaptureHttpServer.start("{"
                + "\"audio_info\":{\"duration\":1280},"
                + "\"result\":{\"utterances\":["
                + "{\"text\":\"hello\",\"start_time\":0,\"end_time\":520},"
                + "{\"text\":\"world\",\"begin_time\":520,\"end_time\":1280}"
                + "]}"
                + "}");
        AsrRequest request = baseRequest();
        request.setUid("uid-1");
        request.setModel("internal-route-model");
        request.setConvertNumbers(true);
        request.setEnableLid(true);
        request.setEnableEmotionDetection(true);
        request.setEnableGenderDetection(true);
        request.setShowVolume(true);
        request.setShowSpeechRate(true);
        HuoshanProperty property = property(null, "latest-api-key", null, null);
        EndpointProcessData processData = processData();

        FlashAsrResponse response = adaptor.asr(request, server.url(), property, processData);

        CapturedRequest captured = server.awaitRequest();
        assertEquals("POST", captured.method);
        assertEquals(HuoshanBigModelFlashAsr.DEFAULT_RESOURCE_ID, captured.header("X-Api-Resource-Id"));
        assertEquals("bella-request-id", captured.header("X-Api-Request-Id"));
        assertEquals("-1", captured.header("X-Api-Sequence"));
        assertEquals("latest-api-key", captured.header("X-Api-Key"));
        assertFalse(captured.headers.containsKey("x-api-app-key"));
        assertFalse(captured.headers.containsKey("x-api-access-key"));

        Map<String, Object> body = JacksonUtils.toMap(captured.body);
        Map<String, Object> user = map(body.get("user"));
        Map<String, Object> audio = map(body.get("audio"));
        Map<String, Object> options = map(body.get("request"));
        assertEquals("uid-1", user.get("uid"));
        assertEquals("wav", audio.get("format"));
        assertEquals("pcm", audio.get("codec"));
        assertEquals(16000, audio.get("rate"));
        assertEquals(16, audio.get("bits"));
        assertEquals(1, audio.get("channel"));
        assertEquals(Base64.getEncoder().encodeToString("audio-bytes".getBytes(StandardCharsets.UTF_8)), audio.get("data"));
        assertEquals("bigmodel", options.get("model_name"));
        assertEquals(true, options.get("enable_itn"));
        assertFalse(captured.body.contains("enable_lid"));
        assertFalse(captured.body.contains("enable_emotion_detection"));
        assertFalse(captured.body.contains("enable_gender_detection"));
        assertFalse(captured.body.contains("show_volume"));
        assertFalse(captured.body.contains("show_speech_rate"));

        assertEquals("provider-logid", response.getTaskId());
        assertEquals("bella-user", response.getUser());
        assertEquals(1280, response.getFlashResult().getDuration());
        assertEquals(2, response.getFlashResult().getSentences().size());
        assertEquals("hello", response.getFlashResult().getSentences().get(0).getText());
        assertEquals(0L, response.getFlashResult().getSentences().get(0).getBeginTime());
        assertEquals(520L, response.getFlashResult().getSentences().get(0).getEndTime());
        assertEquals("world", response.getFlashResult().getSentences().get(1).getText());
        assertEquals(520L, response.getFlashResult().getSentences().get(1).getBeginTime());
    }

    @Test
    public void legacyConsoleAuthUsesAppKeyAndAccessKey() throws Exception {
        CaptureHttpServer server = CaptureHttpServer.start("{\"audio_info\":{\"duration\":10},\"result\":{\"text\":\"ok\"}}");
        AsrRequest request = baseRequest();
        request.setUid(null);
        HuoshanProperty property = property("legacy-app", "legacy-api-key", "legacy-secret", "custom-resource");

        adaptor.asr(request, server.url(), property, processData());

        CapturedRequest captured = server.awaitRequest();
        assertEquals("legacy-app", captured.header("X-Api-App-Key"));
        assertEquals("legacy-secret", captured.header("X-Api-Access-Key"));
        assertEquals("custom-resource", captured.header("X-Api-Resource-Id"));
        assertFalse(captured.headers.containsKey("x-api-key"));
        Map<String, Object> user = map(JacksonUtils.toMap(captured.body).get("user"));
        assertEquals("legacy-app", user.get("uid"));
    }

    @Test
    public void legacyConsoleAccessKeyFallsBackToAuthApiKey() throws Exception {
        CaptureHttpServer server = CaptureHttpServer.start("{\"audio_info\":{\"duration\":10},\"result\":{\"text\":\"ok\"}}");
        HuoshanProperty property = property("legacy-app", "legacy-api-key", null, "custom-resource");

        adaptor.asr(baseRequest(), server.url(), property, processData());

        CapturedRequest captured = server.awaitRequest();
        assertEquals("legacy-api-key", captured.header("X-Api-Access-Key"));
    }

    @Test
    public void responseTextFallsBackToSingleSentenceWhenUtterancesMissing() {
        HuoshanBigModelFlashAsrResponse huoshanResponse = JacksonUtils.deserialize("{"
                + "\"audio_info\":{\"duration\":3210},"
                + "\"result\":{\"text\":\"full text\"}"
                + "}", HuoshanBigModelFlashAsrResponse.class);

        FlashAsrResponse response = adaptor.convertToFlashAsrResponse(huoshanResponse, processData());

        assertEquals(3210, response.getFlashResult().getDuration());
        assertEquals(1, response.getFlashResult().getSentences().size());
        FlashAsrResponse.Sentence sentence = response.getFlashResult().getSentences().get(0);
        assertEquals("full text", sentence.getText());
        assertEquals(0L, sentence.getBeginTime());
        assertEquals(3210L, sentence.getEndTime());
    }

    @Test
    public void nonSuccessHuoshanStatusHeaderThrowsChannelExceptionWithDetails() throws Exception {
        CaptureHttpServer server = CaptureHttpServer.start("{\"message\":\"body-message\",\"logid\":\"body-logid\"}", "40000000",
                "header-message", "header-logid");

        try {
            adaptor.asr(baseRequest(), server.url(), property(null, "latest-api-key", null, null), processData());
            fail("expected Huoshan status header to fail");
        } catch (BellaException.ChannelException e) {
            assertTrue(e.getMessage().contains("40000000"));
            assertTrue(e.getMessage().contains("header-message"));
            assertTrue(e.getMessage().contains("header-logid"));
        } finally {
            server.stop();
        }
    }

    private AsrRequest baseRequest() {
        return AsrRequest.builder()
                .content("audio-bytes".getBytes(StandardCharsets.UTF_8))
                .format("wav")
                .codec("pcm")
                .sampleRate(16000)
                .bits(16)
                .channel(1)
                .enablePunc(true)
                .showUtterances(true)
                .language("zh-CN")
                .build();
    }

    private HuoshanProperty property(String appId, String apiKey, String secret, String deployName) {
        HuoshanProperty property = new HuoshanProperty();
        property.setAppid(appId);
        property.setDeployName(deployName);
        property.setAuth(AuthorizationProperty.builder().apiKey(apiKey).secret(secret).build());
        return property;
    }

    private EndpointProcessData processData() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestId("bella-request-id");
        processData.setChannelRequestId("initial-task-id");
        processData.setUser("bella-user");
        return processData;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object object) {
        assertNotNull(object);
        return (Map<String, Object>) object;
    }

    private static class CaptureHttpServer {
        private final HttpServer server;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile CapturedRequest capturedRequest;

        private CaptureHttpServer(HttpServer server) {
            this.server = server;
        }

        static CaptureHttpServer start(String responseBody) throws IOException {
            return start(responseBody, HuoshanBigModelFlashAsr.SUCCESS_STATUS_CODE, "ok", "provider-logid");
        }

        static CaptureHttpServer start(String responseBody, String statusCode, String message, String logId) throws IOException {
            CaptureHttpServer captureServer = new CaptureHttpServer(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
            captureServer.server.createContext("/asr", exchange -> captureServer.handle(exchange, responseBody, statusCode, message, logId));
            captureServer.server.start();
            return captureServer;
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/asr";
        }

        CapturedRequest awaitRequest() throws InterruptedException {
            assertTrue("http request was not received", latch.await(5, TimeUnit.SECONDS));
            stop();
            return capturedRequest;
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange, String responseBody, String statusCode, String message, String logId) throws IOException {
            try {
                capturedRequest = new CapturedRequest(exchange.getRequestMethod(), headers(exchange.getRequestHeaders()), read(exchange.getRequestBody()));
                Headers responseHeaders = exchange.getResponseHeaders();
                responseHeaders.add("Content-Type", "application/json");
                responseHeaders.add("X-Api-Status-Code", statusCode);
                responseHeaders.add("X-Api-Message", message);
                responseHeaders.add("X-Tt-Logid", logId);
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(bytes);
                }
            } finally {
                latch.countDown();
            }
        }

        private Map<String, String> headers(Headers headers) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                result.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().isEmpty() ? null : entry.getValue().get(0));
            }
            return result;
        }

        private String read(InputStream inputStream) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            int read;
            while ((read = inputStream.read(data)) != -1) {
                buffer.write(data, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static class CapturedRequest {
        private final String method;
        private final Map<String, String> headers;
        private final String body;

        private CapturedRequest(String method, Map<String, String> headers, String body) {
            this.method = method;
            this.headers = headers;
            this.body = body;
        }

        private String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }
}
