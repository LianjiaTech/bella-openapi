package com.ke.bella.openapi.protocol.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.theokanning.openai.batch.BatchRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class HuoshanJobBatchAdaptorTest {

    private HuoshanJobBatchAdaptor adaptor;
    private HttpServer server;
    private AtomicReference<String> requestBody;

    @Before
    public void setUp() {
        adaptor = new HuoshanJobBatchAdaptor();
        requestBody = new AtomicReference<>();
    }

    @After
    public void tearDown() {
        if(server != null) {
            server.stop(0);
        }
    }

    @Test
    public void createBatch_usesRequestCompletionWindow() throws Exception {
        startServer();

        adaptor.createBatch(request("7d"), url(), property());

        assertEquals("7d", payload().get("CompletionWindow"));
    }

    @Test
    public void createBatch_fallsBackToOneDayWhenCompletionWindowIsBlank() throws Exception {
        startServer();

        adaptor.createBatch(request(""), url(), property());

        assertEquals("1d", payload().get("CompletionWindow"));
    }

    private BatchRequest request(String completionWindow) {
        return BatchRequest.builder()
                .inputFileId("input/file.jsonl")
                .endpoint("/v1/chat/completions")
                .completionWindow(completionWindow)
                .build();
    }

    private HuoshanJobProperty property() {
        AuthorizationProperty auth = new AuthorizationProperty();
        auth.setApiKey("ak");
        auth.setSecret("sk");

        HuoshanJobProperty property = new HuoshanJobProperty();
        property.setAuth(auth);
        property.setRegion("cn-beijing");
        property.setTosBucket("bucket-a");
        property.setOutputPrefix("outputs");
        property.setModel("model-a");
        property.setModelVersion("v1");
        return property;
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/ark";
    }

    private Map<String, Object> payload() {
        return JacksonUtils.deserialize(requestBody.get(), new TypeReference<Map<String, Object>>() {
        });
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ark", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestBody.set(read(exchange.getRequestBody()));
        byte[] bytes = "{\"Result\":{\"Id\":\"job-1\"},\"ResponseMetadata\":{}}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
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
