package com.ke.bella.openapi.protocol.tts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class MiniMaxTtsAdaptorTest {

    @Test
    public void convertsOpenAiStyleRequestToMiniMaxRequest() {
        TtsRequest request = baseRequest();

        Map<String, Object> serialized = serialized(request);
        Map<String, Object> voiceSetting = map(serialized.get("voice_setting"));
        Map<String, Object> audioSetting = map(serialized.get("audio_setting"));

        assertEquals("mini-tts", serialized.get("model"));
        assertEquals("hello", serialized.get("text"));
        assertEquals(false, serialized.get("stream"));
        assertFalse(serialized.containsKey("stream_options"));
        assertEquals("hex", serialized.get("output_format"));
        assertEquals("bella_voice", voiceSetting.get("voice_id"));
        assertEquals(1.25, (Double) voiceSetting.get("speed"), 0.00001);
        assertEquals("mp3", audioSetting.get("format"));
        assertEquals(32000, audioSetting.get("sample_rate"));
    }

    @Test
    public void passesThroughExtraBodyAndStandardFieldsWinReservedConflicts() {
        TtsRequest request = baseRequest();

        Map<String, Object> voiceSetting = new HashMap<>();
        voiceSetting.put("voice_id", "extra_voice");
        voiceSetting.put("speed", 0.5);
        voiceSetting.put("emotion", "happy");
        request.setExtraBodyField("voice_setting", voiceSetting);

        Map<String, Object> audioSetting = new HashMap<>();
        audioSetting.put("format", "wav");
        audioSetting.put("sample_rate", 16000);
        audioSetting.put("bitrate", 128000);
        request.setExtraBodyField("audio_setting", audioSetting);

        request.setExtraBodyField("text", "extra text");
        request.setExtraBodyField("output_format", "url");
        request.setExtraBodyField("language_boost", "Chinese");

        Map<String, Object> serialized = serialized(request);
        Map<String, Object> serializedVoiceSetting = map(serialized.get("voice_setting"));
        Map<String, Object> serializedAudioSetting = map(serialized.get("audio_setting"));

        assertEquals("hello", serialized.get("text"));
        assertEquals("hex", serialized.get("output_format"));
        assertEquals("Chinese", serialized.get("language_boost"));

        assertEquals("bella_voice", serializedVoiceSetting.get("voice_id"));
        assertEquals(1.25, (Double) serializedVoiceSetting.get("speed"), 0.00001);
        assertEquals("happy", serializedVoiceSetting.get("emotion"));

        assertEquals("mp3", serializedAudioSetting.get("format"));
        assertEquals(32000, serializedAudioSetting.get("sample_rate"));
        assertEquals(128000, serializedAudioSetting.get("bitrate"));
    }

    @Test
    public void streamRequestSerializesMiniMaxStreamOptions() {
        TtsRequest request = baseRequest();
        request.setStream(true);

        Map<String, Object> serialized = serialized(request, true);
        Map<String, Object> streamOptions = map(serialized.get("stream_options"));

        assertEquals(true, serialized.get("stream"));
        assertEquals(true, streamOptions.get("exclude_aggregated_audio"));
    }

    @Test
    public void syncRequestSerializesStreamFalseWhenTtsRequestUsesDefaultStream() {
        TtsRequest request = new TtsRequest();
        request.setInput("hello");

        Map<String, Object> serialized = serialized(request, false);

        assertEquals(false, serialized.get("stream"));
        assertFalse(serialized.containsKey("stream_options"));
    }

    @Test
    public void usesChannelDefaultsWhenRequestOmitsOptionalFields() {
        TtsRequest request = new TtsRequest();
        request.setInput("hello");
        request.setStream(false);

        Map<String, Object> serialized = serialized(request);
        Map<String, Object> voiceSetting = map(serialized.get("voice_setting"));
        Map<String, Object> audioSetting = map(serialized.get("audio_setting"));

        assertEquals("default_voice", voiceSetting.get("voice_id"));
        assertEquals("wav", audioSetting.get("format"));
        assertEquals(24000, audioSetting.get("sample_rate"));
    }

    @Test
    public void decodesHexAudioFromMiniMaxResponse() {
        MiniMaxResponse response = MiniMaxResponse.builder()
                .data(MiniMaxResponse.DataPayload.builder().audio("48656c6c6f").status(2).build())
                .baseResp(MiniMaxResponse.BaseResp.builder().statusCode(0).statusMsg("success").build())
                .build();

        assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8), MiniMaxAdaptor.decodeAudio(response));
    }

    @Test
    public void convertsMiniMaxErrorResponseToChannelException() {
        MiniMaxResponse response = MiniMaxResponse.builder()
                .baseResp(MiniMaxResponse.BaseResp.builder().statusCode(2013).statusMsg("invalid voice").build())
                .build();

        BellaException.ChannelException exception = MiniMaxAdaptor.toChannelException(response, 200, "OK");

        assertEquals(400, exception.getHttpCode().intValue());
        assertEquals("invalid voice", exception.getMessage());
    }

    @Test
    public void mapsMiniMaxErrorCodesToHttpStatus() {
        assertMiniMaxStatus(1001, 503, 504);
        assertMiniMaxStatus(1002, 429, 429);
        assertMiniMaxStatus(1004, 401, 401);
        assertMiniMaxStatus(1039, 429, 429);
        assertMiniMaxStatus(1042, 400, 400);
        assertMiniMaxStatus(2013, 400, 400);
        assertMiniMaxStatus(1000, 503, 502);
        assertMiniMaxStatus(9999, 503, 502);
    }

    @Test(expected = BellaException.ChannelException.class)
    public void invalidHexAudioThrowsChannelException() {
        MiniMaxAdaptor.decodeHex("not-hex");
    }

    @Test
    public void streamCallbackSendsAudioBytesFromMultipleChunks() {
        RecordingSender sender = new RecordingSender();
        MiniMaxStreamTtsCallback callback = new MiniMaxStreamTtsCallback(sender, null, null);

        callback.callback(bytes("data: {\"data\":{\"audio\":\"4865\",\"status\":1},\"base_resp\":{\"status_code\":0}}\n"));
        callback.callback(bytes("{\"data\":{\"audio\":\"6c6c6f\",\"status\":1},\"base_resp\":{\"status_code\":0}}\n"));
        callback.callback(bytes("data: {\"data\":{\"status\":2},\"base_resp\":{\"status_code\":0}}\n"));

        assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8), sender.audio());
        assertTrue(sender.closed);
    }

    @Test
    public void streamCallbackDoesNotSendAggregatedAudioOnFinalStatus() {
        RecordingSender sender = new RecordingSender();
        MiniMaxStreamTtsCallback callback = new MiniMaxStreamTtsCallback(sender, null, null);

        callback.callback(bytes("data: {\"data\":{\"audio\":\"4869\",\"status\":1},\"base_resp\":{\"status_code\":0}}\n"));
        callback.callback(bytes("data: {\"data\":{\"audio\":\"48694869\",\"status\":2},\"base_resp\":{\"status_code\":0}}\n"));

        assertArrayEquals("Hi".getBytes(StandardCharsets.UTF_8), sender.audio());
        assertTrue(sender.closed);
    }

    @Test
    public void streamCallbackConvertsMiniMaxErrorChunkToChannelException() {
        RecordingSender sender = new RecordingSender();
        AtomicReference<BellaException> exception = new AtomicReference<>();
        MiniMaxStreamTtsCallback callback = new MiniMaxStreamTtsCallback(sender, null, null) {
            @Override
            public void finish(BellaException e) {
                exception.set(e);
                super.finish(e);
            }
        };

        callback.callback(bytes("data: {\"base_resp\":{\"status_code\":2013,\"status_msg\":\"invalid voice\"}}\n"));

        assertTrue(exception.get() instanceof BellaException.ChannelException);
        BellaException.ChannelException channelException = (BellaException.ChannelException) exception.get();
        assertEquals(400, channelException.getHttpCode().intValue());
        assertEquals("invalid voice", channelException.getMessage());
        assertTrue(sender.closed);
    }

    @Test
    public void streamCallbackParsesJsonSplitAcrossCallbackBytes() {
        RecordingSender sender = new RecordingSender();
        MiniMaxStreamTtsCallback callback = new MiniMaxStreamTtsCallback(sender, null, null);

        callback.callback(bytes("data: {\"data\":{\"audio\":\"48"));
        callback.callback(bytes("69\",\"status\":1},\"base_resp\":{\"status_code\":0}}"));
        callback.callback(bytes("\n"));
        callback.callback(bytes("data: {\"data\":{\"status\":2},\"base_resp\":{\"status_code\":0}}\n"));

        assertArrayEquals("Hi".getBytes(StandardCharsets.UTF_8), sender.audio());
        assertTrue(sender.closed);
    }

    @Test
    public void streamCallbackAggregatesMultiLineSseDataEvent() {
        RecordingSender sender = new RecordingSender();
        MiniMaxStreamTtsCallback callback = new MiniMaxStreamTtsCallback(sender, null, null);

        callback.callback(bytes("data: {\"data\":{\n"));
        callback.callback(bytes("data: \"audio\":\"4869\",\"status\":1},\"base_resp\":{\"status_code\":0}}\n\n"));
        callback.callback(bytes("data: {\"data\":{\"status\":2},\"base_resp\":{\"status_code\":0}}\n\n"));

        assertArrayEquals("Hi".getBytes(StandardCharsets.UTF_8), sender.audio());
        assertTrue(sender.closed);
    }

    private TtsRequest baseRequest() {
        TtsRequest request = new TtsRequest();
        request.setModel("tts-model");
        request.setInput("hello");
        request.setVoice("bella_voice");
        request.setResponseFormat("mp3");
        request.setSampleRate(32000);
        request.setSpeed(1.25);
        request.setStream(false);
        return request;
    }

    private MiniMaxProperty property() {
        MiniMaxProperty property = new MiniMaxProperty();
        property.setDeployName("mini-tts");
        property.setDefaultVoice("default_voice");
        property.setDefaultContentType("wav");
        property.setDefaultSampleRate(24000);
        return property;
    }

    private Map<String, Object> serialized(TtsRequest request) {
        return serialized(request, request.isStream());
    }

    private Map<String, Object> serialized(TtsRequest request, boolean stream) {
        return JacksonUtils.toMap(JacksonUtils.serialize(MiniMaxRequest.from(request, property(), stream)));
    }

    private void assertMiniMaxStatus(int miniMaxCode, int expectedExceptionHttpCode, int expectedErrorHttpCode) {
        MiniMaxResponse response = MiniMaxResponse.builder()
                .baseResp(MiniMaxResponse.BaseResp.builder().statusCode(miniMaxCode).statusMsg("error").build())
                .build();
        BellaException.ChannelException exception = MiniMaxAdaptor.toChannelException(response, 200, "OK");

        assertEquals(expectedExceptionHttpCode, exception.getHttpCode().intValue());
        assertEquals(expectedErrorHttpCode, exception.convertToOpenapiError().getHttpCode().intValue());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static class RecordingSender implements Callbacks.Sender {
        private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        private boolean closed;

        @Override
        public void send(String text) {
        }

        @Override
        public void send(byte[] bytes) {
            audio.write(bytes, 0, bytes.length);
        }

        @Override
        public void onError(Throwable e) {
        }

        @Override
        public void close() {
            closed = true;
        }

        private byte[] audio() {
            return audio.toByteArray();
        }
    }
}
