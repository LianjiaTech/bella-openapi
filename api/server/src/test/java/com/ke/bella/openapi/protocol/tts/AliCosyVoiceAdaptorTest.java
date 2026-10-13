package com.ke.bella.openapi.protocol.tts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Request;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class AliCosyVoiceAdaptorTest {
    private static final String ALI_COSYVOICE_URL = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer";

    @Test
    public void convertsOpenAiStyleRequestToAliCosyVoiceRequest() {
        TtsRequest request = baseRequest();

        Map<String, Object> serialized = serialized(request);
        Map<String, Object> input = map(serialized.get("input"));

        assertEquals("cosyvoice-v1", serialized.get("model"));
        assertEquals("hello", input.get("text"));
        assertEquals("longxiaochun", input.get("voice"));
        assertEquals("mp3", input.get("format"));
        assertEquals(32000, input.get("sample_rate"));
        assertEquals(1.25, (Double) input.get("rate"), 0.00001);
    }

    @Test
    public void usesChannelDefaultsWhenRequestOmitsOptionalFields() {
        TtsRequest request = new TtsRequest();
        request.setModel("request-model");
        request.setInput("hello");

        Map<String, Object> serialized = serialized(request);
        Map<String, Object> input = map(serialized.get("input"));

        assertEquals("cosyvoice-v1", serialized.get("model"));
        assertEquals("longwan", input.get("voice"));
        assertEquals("wav", input.get("format"));
        assertEquals(24000, input.get("sample_rate"));
        assertFalse(input.containsKey("rate"));
    }

    @Test
    public void passesThroughExtraBodyAndStandardFieldsWinReservedConflicts() {
        TtsRequest request = baseRequest();

        Map<String, Object> input = new HashMap<>();
        input.put("text", "extra text");
        input.put("voice", "extra voice");
        input.put("format", "wav");
        input.put("sample_rate", 16000);
        input.put("rate", 0.5);
        input.put("word_timestamp_enabled", true);
        request.setExtraBodyField("input", input);
        request.setExtraBodyField("model", "extra-model");
        request.setExtraBodyField("parameters", mapOf("trace", true));

        Map<String, Object> serialized = serialized(request);
        Map<String, Object> serializedInput = map(serialized.get("input"));

        assertEquals("cosyvoice-v1", serialized.get("model"));
        assertEquals("hello", serializedInput.get("text"));
        assertEquals("longxiaochun", serializedInput.get("voice"));
        assertEquals("mp3", serializedInput.get("format"));
        assertEquals(32000, serializedInput.get("sample_rate"));
        assertEquals(1.25, (Double) serializedInput.get("rate"), 0.00001);
        assertEquals(true, serializedInput.get("word_timestamp_enabled"));
        assertEquals(mapOf("trace", true), serialized.get("parameters"));
    }

    @Test
    public void wrappedExtraBodyPassesThrough() {
        TtsRequest request = baseRequest();
        Map<String, Object> wrappedInput = new HashMap<>();
        wrappedInput.put("emotion", "happy");
        Map<String, Object> wrapped = new HashMap<>();
        wrapped.put("input", wrappedInput);
        wrapped.put("metadata", mapOf("scene", "story"));
        request.setExtraBodyField("extra_body", wrapped);

        Map<String, Object> serialized = serialized(request);
        Map<String, Object> serializedInput = map(serialized.get("input"));

        assertFalse(serialized.containsKey("extra_body"));
        assertEquals("happy", serializedInput.get("emotion"));
        assertEquals(mapOf("scene", "story"), serialized.get("metadata"));
    }

    @Test
    public void resolvesAudioUrlFromNonStreamResponse() {
        AliCosyVoiceResponse response = AliCosyVoiceResponse.builder()
                .output(AliCosyVoiceResponse.Output.builder()
                        .audio(AliCosyVoiceResponse.Audio.builder().url("https://example.com/audio.mp3").build())
                        .build())
                .build();

        assertEquals("https://example.com/audio.mp3", AliCosyVoiceAdaptor.resolveAudioUrl(response));
    }

    @Test(expected = BellaException.ChannelException.class)
    public void missingAudioUrlThrowsChannelException() {
        AliCosyVoiceAdaptor.resolveAudioUrl(AliCosyVoiceResponse.builder()
                .output(AliCosyVoiceResponse.Output.builder().audio(new AliCosyVoiceResponse.Audio()).build())
                .build());
    }

    @Test
    public void ttsDownloadsAudioUrlFromSynthesisResponse() {
        TestAliCosyVoiceAdaptor adaptor = new TestAliCosyVoiceAdaptor("audio-url", "audio".getBytes(StandardCharsets.UTF_8));

        byte[] data = adaptor.tts(baseRequest(), ALI_COSYVOICE_URL, property());

        assertArrayEquals("audio".getBytes(StandardCharsets.UTF_8), data);
        assertEquals("audio-url", adaptor.downloadedUrl);
    }

    @Test
    public void streamCallbackSendsBase64AudioBytes() {
        RecordingSender sender = new RecordingSender();
        AliCosyVoiceStreamTtsCallback callback = new AliCosyVoiceStreamTtsCallback(sender, null, null);
        String audio = Base64.getEncoder().encodeToString("Hello".getBytes(StandardCharsets.UTF_8));

        callback.callback(bytes("event: result-generated\n"));
        callback.callback(bytes("data: {\"output\":{\"audio\":{\"data\":\"" + audio + "\"}}}\n\n"));
        callback.callback(bytes("event: task-finished\n"));
        callback.callback(bytes("data: {\"output\":{}}\n\n"));

        assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8), sender.audio());
        assertTrue(sender.closed);
    }

    @Test
    public void streamCallbackClosesOnEmptyTaskFinishedEvent() {
        RecordingSender sender = new RecordingSender();
        AliCosyVoiceStreamTtsCallback callback = new AliCosyVoiceStreamTtsCallback(sender, null, null);

        callback.callback(bytes("event: task-finished\n\n"));

        assertArrayEquals(new byte[0], sender.audio());
        assertTrue(sender.closed);
    }

    @Test
    public void streamCallbackConvertsErrorEventToChannelException() {
        RecordingSender sender = new RecordingSender();
        AtomicReference<BellaException> exception = new AtomicReference<>();
        AliCosyVoiceStreamTtsCallback callback = new AliCosyVoiceStreamTtsCallback(sender, null, null) {
            @Override
            public void finish(BellaException e) {
                exception.set(e);
                super.finish(e);
            }
        };

        callback.callback(bytes("event: task-failed\n"));
        callback.callback(bytes("data: {\"code\":\"InvalidParameter\",\"message\":\"bad voice\"}\n\n"));

        assertTrue(exception.get() instanceof BellaException.ChannelException);
        BellaException.ChannelException channelException = (BellaException.ChannelException) exception.get();
        assertEquals(503, channelException.getHttpCode().intValue());
        assertEquals("bad voice", channelException.getMessage());
        assertTrue(sender.closed);
    }

    @Test
    public void buildsBearerAuthProperty() {
        AliCosyVoiceProperty property = property();
        AuthorizationProperty auth = new AuthorizationProperty();
        auth.setType(AuthorizationProperty.AuthType.BEARER);
        auth.setApiKey("sk-test");
        property.setAuth(auth);

        assertEquals("sk-test", property.getAuth().getApiKey());
    }

    private TtsRequest baseRequest() {
        TtsRequest request = new TtsRequest();
        request.setModel("request-model");
        request.setInput("hello");
        request.setVoice("longxiaochun");
        request.setResponseFormat("mp3");
        request.setSampleRate(32000);
        request.setSpeed(1.25);
        request.setStream(false);
        return request;
    }

    private AliCosyVoiceProperty property() {
        AliCosyVoiceProperty property = new AliCosyVoiceProperty();
        property.setDeployName("cosyvoice-v1");
        property.setDefaultVoice("longwan");
        property.setDefaultContentType("wav");
        property.setDefaultSampleRate(24000);
        return property;
    }

    private Map<String, Object> serialized(TtsRequest request) {
        return JacksonUtils.toMap(JacksonUtils.serialize(AliCosyVoiceRequest.from(request, property())));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static class TestAliCosyVoiceAdaptor extends AliCosyVoiceAdaptor {
        private final String audioUrl;
        private final byte[] audioBytes;
        private String downloadedUrl;

        private TestAliCosyVoiceAdaptor(String audioUrl, byte[] audioBytes) {
            this.audioUrl = audioUrl;
            this.audioBytes = audioBytes;
        }

        @Override
        protected AliCosyVoiceResponse requestSynthesis(Request httpRequest) {
            return AliCosyVoiceResponse.builder()
                    .output(AliCosyVoiceResponse.Output.builder()
                            .audio(AliCosyVoiceResponse.Audio.builder().url(audioUrl).build())
                            .build())
                    .build();
        }

        @Override
        protected byte[] downloadAudio(String audioUrl) {
            downloadedUrl = audioUrl;
            return audioBytes;
        }
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
