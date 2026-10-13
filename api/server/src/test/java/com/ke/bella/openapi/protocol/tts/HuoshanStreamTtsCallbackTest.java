package com.ke.bella.openapi.protocol.tts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.WebSocket;
import okio.ByteString;

public class HuoshanStreamTtsCallbackTest {

    @Test
    public void convertsSpeedToSpeechRate() {
        TtsRequest request = requestWithSpeed(1.5);

        Map<String, Object> audioParams = audioParams(request);

        assertEquals(50, audioParams.get("speech_rate"));
    }

    @Test
    public void doesNotSendDefaultSpeedAsSpeechRate() {
        TtsRequest request = requestWithSpeed(1.0);

        Map<String, Object> audioParams = audioParams(request);

        assertFalse(audioParams.containsKey("speech_rate"));
    }

    @Test
    public void requestAudioParamsSpeechRateOverridesSpeed() {
        TtsRequest request = requestWithSpeed(1.5);
        Map<String, Object> requestExtra = new HashMap<>();
        Map<String, Object> audioParams = new HashMap<>();
        audioParams.put("speech_rate", 12);
        requestExtra.put("audio_params", audioParams);
        request.setExtraBodyField("request", requestExtra);

        Map<String, Object> payloadAudioParams = audioParams(request);

        assertEquals(12, payloadAudioParams.get("speech_rate"));
    }

    @Test
    public void audioParamsSpeechRateOverridesSpeed() {
        TtsRequest request = requestWithSpeed(1.5);
        Map<String, Object> audioParams = new HashMap<>();
        audioParams.put("speech_rate", -30);
        request.setExtraBodyField("audio_params", audioParams);

        Map<String, Object> payloadAudioParams = audioParams(request);

        assertEquals(-30, payloadAudioParams.get("speech_rate"));
    }

    @Test
    public void closesWebSocketAfterSessionFinished() {
        TtsRequest request = requestWithSpeed(1.0);
        Callbacks.Sender sender = mock(Callbacks.Sender.class);
        EndpointProcessData processData = new EndpointProcessData();
        EndpointLogger logger = mock(EndpointLogger.class);
        HuoshanStreamTtsCallback callback = new HuoshanStreamTtsCallback(request, sender, processData, logger);
        WebSocket webSocket = mock(WebSocket.class);
        when(webSocket.send(any(ByteString.class))).thenReturn(true);

        callback.onMessage(webSocket, ByteString.of(sessionFinishedFrame()));

        ArgumentCaptor<ByteString> sentMessage = ArgumentCaptor.forClass(ByteString.class);
        InOrder inOrder = inOrder(webSocket);
        inOrder.verify(webSocket).send(sentMessage.capture());
        inOrder.verify(webSocket).close(1000, "session finished");
        assertEquals(2, callback.bytesToInt(slice(sentMessage.getValue().toByteArray(), 4, 4)));
        verify(sender).close();
        verify(logger).log(processData);
    }

    @Test
    public void recordsProviderLogAndConnectIdsOnOpen() {
        TtsRequest request = requestWithSpeed(1.0);
        EndpointProcessData processData = new EndpointProcessData();
        HuoshanStreamTtsCallback callback = new HuoshanStreamTtsCallback(request, mock(Callbacks.Sender.class), processData, null);
        WebSocket webSocket = mock(WebSocket.class);
        okhttp3.Request upstreamRequest = new okhttp3.Request.Builder()
                .url("wss://provider")
                .header("X-Api-Connect-Id", "connect-123")
                .build();
        okhttp3.Response response = new okhttp3.Response.Builder()
                .request(upstreamRequest)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .header("X-Tt-Logid", "log-123")
                .build();

        callback.onOpen(webSocket, response);

        assertEquals("log-123", processData.getUpstreamLogId());
        assertEquals("connect-123", processData.getUpstreamConnectId());
        assertEquals("log-123", processData.getChannelRequestId());
    }

    private TtsRequest requestWithSpeed(Double speed) {
        TtsRequest request = new TtsRequest();
        request.setInput("hello");
        request.setSpeed(speed);
        request.setResponseFormat("wav");
        return request;
    }

    private Map<String, Object> audioParams(TtsRequest request) {
        HuoshanStreamTtsCallback.PayloadJ payload = new HuoshanStreamTtsCallback.PayloadJ(request, 200);
        Map<String, Object> payloadMap = JacksonUtils.toMap(JacksonUtils.serialize(payload));
        Map<String, Object> reqParams = (Map<String, Object>) payloadMap.get("req_params");
        return (Map<String, Object>) reqParams.get("audio_params");
    }

    private byte[] sessionFinishedFrame() {
        byte[] sessionId = "session".getBytes(StandardCharsets.UTF_8);
        byte[] meta = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[4 + 4 + 4 + sessionId.length + 4 + meta.length];
        frame[0] = 0x11;
        frame[1] = (byte) 0x94;
        frame[2] = 0x10;
        System.arraycopy(HuoshanStreamTtsCallback.intToBytes(152), 0, frame, 4, 4);
        System.arraycopy(HuoshanStreamTtsCallback.intToBytes(sessionId.length), 0, frame, 8, 4);
        System.arraycopy(sessionId, 0, frame, 12, sessionId.length);
        int metaOffset = 12 + sessionId.length;
        System.arraycopy(HuoshanStreamTtsCallback.intToBytes(meta.length), 0, frame, metaOffset, 4);
        System.arraycopy(meta, 0, frame, metaOffset + 4, meta.length);
        return frame;
    }

    private byte[] slice(byte[] source, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }
}
