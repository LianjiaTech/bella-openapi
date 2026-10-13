package com.ke.bella.openapi.protocol.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.db.log.ConsoleLogRepo;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.asr.HuoshanProperty;
import com.ke.bella.openapi.protocol.asr.HuoshanRealTimeAsrRequest;
import com.ke.bella.openapi.protocol.asr.HuoshanStreamLMAsrCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.JacksonUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;

public class AsrLifecycleLoggingTest {
    @Test
    public void closeLogsRequestAndHandshakeIdsTogether() throws Exception {
        verifyLifecycleLog(false);
    }

    @Test
    public void failureThenCloseLogsErrorAndHandshakeIdsOnce() throws Exception {
        verifyLifecycleLog(true);
    }

    @SuppressWarnings("unchecked")
    private void verifyLifecycleLog(boolean fail) throws Exception {
        EndpointProcessData data = new EndpointProcessData();
        data.setEndpoint("/v1/audio/asr/stream");
        data.setModel("huoshanLM-realtime-asr");
        data.setRequestId("request-test");
        data.setBellaTraceId("trace-test");
        data.setApikey("test-secret-not-for-logging");
        HuoshanProperty property = new HuoshanProperty();
        property.setAuth(new AuthorizationProperty());
        EndpointLogger endpointLogger = mock(EndpointLogger.class);
        ConsoleLogRepo repo = new ConsoleLogRepo();
        doAnswer(invocation -> {
            repo.record(invocation.getArgument(0));
            return null;
        }).when(endpointLogger).log(data);
        RealTimeAdaptor<AsrProperty> adaptor = mock(RealTimeAdaptor.class);
        WebSocket upstream = mock(WebSocket.class);
        when(upstream.send(any(okio.ByteString.class))).thenReturn(true);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(adaptor.createCallback(any(), any(), any(), anyString(), any(), any())).thenAnswer(invocation ->
                new HuoshanStreamLMAsrCallback(new HuoshanRealTimeAsrRequest(invocation.getArgument(4, RealTimeMessage.class), property),
                        invocation.getArgument(0), data, endpointLogger, response -> java.util.Collections.emptyList()));
        when(adaptor.startTranscription(anyString(), any(), any(), any())).thenAnswer(invocation -> {
            Callbacks.WebSocketCallback callback = invocation.getArgument(3);
            callback.onOpen(upstream, new Response.Builder()
                    .request(new Request.Builder().url("http://localhost")
                            .header("X-Api-Connect-Id", "connect-test").build())
                    .protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols")
                    .header("X-Tt-Logid", "log-test").build());
            return upstream;
        });
        RealTimeHandler handler = new RealTimeHandler("http://localhost", property, data, endpointLogger, adaptor);
        Logger logger = (Logger) LoggerFactory.getLogger(ConsoleLogRepo.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            handler.handleTextMessage(session, new TextMessage("{\"header\":{\"name\":\"StartTranscription\","
                    + "\"message_id\":\"message-test\"},\"payload\":{\"format\":\"pcm\",\"sample_rate\":16000,"
                    + "\"enable_speaker_info\":true}}"));
            org.mockito.ArgumentCaptor<Callbacks.WebSocketCallback> callbackCaptor =
                    org.mockito.ArgumentCaptor.forClass(Callbacks.WebSocketCallback.class);
            verify(adaptor).startTranscription(anyString(), any(), any(), callbackCaptor.capture());
            Callbacks.WebSocketCallback callback = callbackCaptor.getValue();
            if(fail) {
                callback.onFailure(upstream, new IOException("provider connection failed"), null);
            }
            callback.onClosing(upstream, 1000, "done");
            callback.onClosed(upstream, 1000, "done");
            verify(endpointLogger, times(1)).log(data);
            assertEquals(1, appender.list.size());
            String json = appender.list.get(0).getFormattedMessage();
            JsonNode record = JacksonUtils.deserialize(json);
            assertEquals("request-test", record.path("requestId").asText());
            assertEquals("trace-test", record.path("bellaTraceId").asText());
            assertEquals("huoshanLM-realtime-asr", record.path("model").asText());
            assertEquals("log-test", record.path("upstreamLogId").asText());
            assertEquals("connect-test", record.path("upstreamConnectId").asText());
            assertNotNull(record.path("request").path("header").get("task_id"));
            assertEquals("message-test", record.path("request").path("header").path("message_id").asText());
            assertEquals(16000, record.path("request").path("payload").path("sample_rate").asInt());
            assertEquals(true, record.path("request").path("payload").path("enable_speaker_info").asBoolean());
            assertFalse(json.contains("test-secret-not-for-logging"));
            if(fail) {
                assertEquals("provider connection failed", record.path("response").path("error").path("message").asText());
            }
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
