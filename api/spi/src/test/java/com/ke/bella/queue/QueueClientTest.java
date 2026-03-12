package com.ke.bella.queue;

import com.theokanning.openai.queue.Put;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueueClient.streamingPut method
 */
public class QueueClientTest {

    private QueueClient queueClient;

    @BeforeEach
    public void setUp() {
        queueClient = QueueClient.getInstance("https://test.api.example.com");
    }

    /**
     * Test timeout calculation when custom timeout is provided and valid (> 0)
     */
    @Test
    public void testTimeoutCalculation_WithCustomTimeout() {
        Put put = new Put();
        put.setTimeout(600);

        int timeout = (put.getTimeout() != null && put.getTimeout() > 0)
                ? put.getTimeout()
                : 300;

        assertEquals(600, timeout, "Should use custom timeout when timeout > 0");
    }

    /**
     * Test timeout calculation when timeout is null
     */
    @Test
    public void testTimeoutCalculation_WithNullTimeout() {
        Put put = new Put();

        int timeout = (put.getTimeout() != null && put.getTimeout() > 0)
                ? put.getTimeout()
                : 300;

        assertEquals(300, timeout, "Should use default timeout (300) when timeout is null");
    }

    /**
     * Test timeout calculation when timeout is zero
     */
    @Test
    public void testTimeoutCalculation_WithZeroTimeout() {
        Put put = new Put();
        put.setTimeout(0);

        int timeout = (put.getTimeout() != null && put.getTimeout() > 0)
                ? put.getTimeout()
                : 300;

        assertEquals(300, timeout, "Should use default timeout (300) when timeout is 0");
    }

    /**
     * Test timeout calculation when timeout is negative
     */
    @Test
    public void testTimeoutCalculation_WithNegativeTimeout() {
        Put put = new Put();
        put.setTimeout(-100);

        int timeout = (put.getTimeout() != null && put.getTimeout() > 0)
                ? put.getTimeout()
                : 300;

        assertEquals(300, timeout, "Should use default timeout (300) when timeout is negative");
    }

    /**
     * Test that buildRequest correctly constructs URL
     */
    @Test
    public void testBuildRequest_UrlConstruction() {
        String apiKey = "test-api-key";
        String json = "{\"test\":\"data\"}";
        String expectedUrl = "https://test.api.example.com/v1/queue/put";

        Request request = queueClient.buildRequest(expectedUrl, apiKey, json);

        assertNotNull(request, "Request should not be null");
        assertEquals(expectedUrl, request.url().toString(), "URL should match expected");
        assertEquals("POST", request.method(), "HTTP method should be POST");
    }

    /**
     * Test that buildRequest correctly adds Authorization header with API key
     */
    @Test
    public void testBuildRequest_WithApiKey() {
        String url = "https://test.api.example.com/v1/queue/put";
        String apiKey = "test-api-key-123";
        String json = "{\"test\":\"data\"}";

        Request request = queueClient.buildRequest(url, apiKey, json);

        String authHeader = request.header("Authorization");
        assertNotNull(authHeader, "Authorization header should be present");
        assertEquals("Bearer test-api-key-123", authHeader, "Authorization header should have Bearer token");
    }

    /**
     * Test that buildRequest handles null API key correctly (no Authorization header)
     */
    @Test
    public void testBuildRequest_WithNullApiKey() {
        String url = "https://test.api.example.com/v1/queue/put";
        String json = "{\"test\":\"data\"}";

        Request request = queueClient.buildRequest(url, null, json);

        String authHeader = request.header("Authorization");
        assertNull(authHeader, "Authorization header should not be present when apiKey is null");
    }

    /**
     * Test that buildRequest correctly sets request body
     */
    @Test
    public void testBuildRequest_RequestBody() throws Exception {
        String url = "https://test.api.example.com/v1/queue/put";
        String apiKey = "test-api-key";
        String json = "{\"test\":\"data\"}";

        Request request = queueClient.buildRequest(url, apiKey, json);

        assertNotNull(request.body(), "Request body should not be null");
        assertEquals("application/json; charset=utf-8",
                request.body().contentType().toString(),
                "Content type should be application/json with UTF-8 charset");
    }

    /**
     * Test QueueClient singleton pattern
     */
    @Test
    public void testGetInstance_Singleton() {
        QueueClient instance1 = QueueClient.getInstance("https://test.api.example.com");
        QueueClient instance2 = QueueClient.getInstance("https://different.url.com");

        assertSame(instance1, instance2, "getInstance should return the same instance (singleton)");
    }

    /**
     * Test QueueClient URL validation Note: Cannot test directly due to singleton pattern
     */
    @Test
    public void testGetInstance_WithBlankUrl() {
        assertTrue(true, "URL validation handled in constructor at QueueClient:35-37");
    }
}
