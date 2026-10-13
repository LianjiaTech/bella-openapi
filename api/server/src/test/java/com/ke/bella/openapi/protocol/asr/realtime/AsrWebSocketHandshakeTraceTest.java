package com.ke.bella.openapi.protocol.asr.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.ke.bella.openapi.EndpointProcessData;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

public class AsrWebSocketHandshakeTraceTest {
    @Test
    public void capturesBothHeadersAndKeepsLogIdAsChannelRequestId() {
        EndpointProcessData processData = new EndpointProcessData();
        Response response = responseBuilder().header("X-Tt-Logid", " log-123 ")
                .header("X-Api-Connect-Id", "connect-456").build();

        AsrWebSocketHandshakeTrace.Values values = AsrWebSocketHandshakeTrace.capture(response, processData);

        assertEquals("log-123", values.getLogId());
        assertEquals("connect-456", values.getConnectId());
        assertEquals("log-123", processData.getChannelRequestId());
        assertEquals("log-123", processData.getUpstreamLogId());
        assertEquals("connect-456", processData.getUpstreamConnectId());
    }

    @Test
    public void ignoresMissingHeadersWithoutOverwritingExistingChannelRequestId() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setChannelRequestId("voice-123");
        Response response = responseBuilder().build();

        AsrWebSocketHandshakeTrace.Values values = AsrWebSocketHandshakeTrace.capture(response, processData);

        assertNull(values.getLogId());
        assertNull(values.getConnectId());
        assertEquals("voice-123", processData.getChannelRequestId());
        assertNull(processData.getUpstreamLogId());
        assertNull(processData.getUpstreamConnectId());
    }

    @Test
    public void keepsExistingChannelRequestIdWhenLogIdIsPresent() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setChannelRequestId("voice-123");
        Response response = responseBuilder().header("X-Tt-Logid", "log-123").build();

        AsrWebSocketHandshakeTrace.capture(response, processData);

        assertEquals("voice-123", processData.getChannelRequestId());
        assertEquals("log-123", processData.getUpstreamLogId());
    }

    @Test
    public void usesRequestConnectIdWhenProviderDoesNotEchoIt() {
        EndpointProcessData processData = new EndpointProcessData();
        Response response = responseBuilder().request(new Request.Builder().url("http://localhost")
                .header("X-Api-Connect-Id", "connect-456").build()).build();

        AsrWebSocketHandshakeTrace.Values values = AsrWebSocketHandshakeTrace.capture(response, processData);

        assertEquals("connect-456", values.getConnectId());
        assertEquals("connect-456", processData.getUpstreamConnectId());
    }

    private Response.Builder responseBuilder() {
        return new Response.Builder().request(new Request.Builder().url("http://localhost").build())
                .protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols");
    }
}
