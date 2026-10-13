package com.ke.bella.openapi.protocol.batch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.theokanning.openai.queue.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OpenAIBatchAdaptorTest {

    private OpenAIBatchAdaptor adaptor;
    private HttpServer server;
    private AtomicInteger calls;

    @Before
    public void setUp() {
        adaptor = new OpenAIBatchAdaptor();
        calls = new AtomicInteger();
    }

    @After
    public void tearDown() {
        if(server != null) {
            server.stop(0);
        }
    }

    @Test
    public void uploadTasks_returnsFileIdOnSuccess() throws Exception {
        startServer(exchange -> {
            calls.incrementAndGet();
            write(exchange, 200, "{\"id\":\"file-1\"}");
        });

        assertEquals("file-1", adaptor.uploadTasks(Collections.singletonList(task()), property()));
        assertEquals(1, calls.get());
    }

    @Test
    public void uploadTasks_wrapsFailedStatusAsRetriableExceptionWithoutRetrying() throws Exception {
        startServer(exchange -> {
            calls.incrementAndGet();
            write(exchange, 429, "{\"error\":\"rate limit\"}");
        });

        try {
            adaptor.uploadTasks(Collections.singletonList(task()), property());
            fail("expected upload to fail");
        } catch (BatchRetriableException e) {
            assertEquals(1, calls.get());
        }
    }

    @Test
    public void uploadTasks_wrapsIoFailureAsRetriableException() throws Exception {
        startServer(exchange -> {
            calls.incrementAndGet();
            exchange.close();
        });

        try {
            adaptor.uploadTasks(Collections.singletonList(task()), property());
            fail("expected upload to fail");
        } catch (BatchRetriableException e) {
            assertEquals(1, calls.get());
        }
    }

    private OpenAiProperty property() {
        OpenAiProperty property = new OpenAiProperty();
        property.setFileServiceUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/files");
        return property;
    }

    private Task task() {
        Task task = mock(Task.class);
        when(task.getTaskId()).thenReturn("task-1");
        when(task.getEndpoint()).thenReturn("/v1/chat/completions");
        when(task.getData()).thenReturn(Collections.singletonMap("model", "gpt-4"));
        return task;
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/files", exchange -> {
            exchange.getRequestBody().close();
            handler.handle(exchange);
        });
        server.start();
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
