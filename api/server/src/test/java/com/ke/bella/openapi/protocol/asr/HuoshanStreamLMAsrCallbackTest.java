package com.ke.bella.openapi.protocol.asr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import com.ke.bella.openapi.utils.JacksonUtils;

public class HuoshanStreamLMAsrCallbackTest {

    @Test
    public void fullClientRequestIncludesEnableNonstreamWhenProvided() throws Exception {
        RealTimeMessage message = startTranscriptionMessage(Boolean.TRUE, null, null, null, null);

        Map<String, Object> request = fullClientRequest(message);

        assertEquals(Boolean.TRUE, request.get("enable_nonstream"));
        assertEquals(Boolean.TRUE, request.get("enable_punc"));
        assertEquals(Boolean.TRUE, request.get("enable_itn"));
    }

    @Test
    public void fullClientRequestIncludesSpeakerInfoAndGenderDetectionWhenProvided() throws Exception {
        RealTimeMessage message = startTranscriptionMessage(null, Boolean.TRUE, Boolean.TRUE, 1200, "200");

        Map<String, Object> request = fullClientRequest(message);

        assertEquals(Boolean.TRUE, request.get("enable_speaker_info"));
        assertEquals(Boolean.TRUE, request.get("enable_gender_detection"));
        assertEquals(1200, request.get("end_window_size"));
        assertEquals("200", request.get("ssd_version"));
        assertFalse(request.containsKey("enable_nonstream"));
    }

    @Test
    public void fullClientRequestOmitsEnableNonstreamWhenUnset() throws Exception {
        RealTimeMessage message = startTranscriptionMessage(null, null, null, null, null);

        Map<String, Object> request = fullClientRequest(message);

        assertFalse(request.containsKey("enable_nonstream"));
        assertFalse(request.containsKey("enable_speaker_info"));
        assertFalse(request.containsKey("enable_gender_detection"));
        assertFalse(request.containsKey("end_window_size"));
        assertFalse(request.containsKey("ssd_version"));
    }

    @Test
    public void flashFullClientRequestIncludesHuoshanLmOptions() throws Exception {
        AsrRequest asrRequest = AsrRequest.builder()
                .format("ogg")
                .codec("opus")
                .sampleRate(16000)
                .bits(16)
                .channel(2)
                .language("en-US")
                .uid("user-1")
                .did("device-1")
                .platform("Linux")
                .sdkVersion("1.0.0")
                .appVersion("2.0.0")
                .enableNonstream(Boolean.TRUE)
                .enableItn(Boolean.TRUE)
                .enablePunc(Boolean.TRUE)
                .enableDdc(Boolean.TRUE)
                .outputZhVariant("traditional")
                .enableAutoLang(Boolean.TRUE)
                .showUtterances(Boolean.FALSE)
                .showSpeechRate(Boolean.TRUE)
                .showVolume(Boolean.TRUE)
                .enableLid(Boolean.TRUE)
                .enableEmotionDetection(Boolean.TRUE)
                .enableGenderDetection(Boolean.TRUE)
                .resultType("full")
                .enableAccelerateText(Boolean.TRUE)
                .accelerateScore(0.75)
                .vadSegmentDuration(3000)
                .endWindowSize(1200)
                .forceToSpeechTime(1000)
                .sensitiveWordsFilter("{\"system_reserved_filter\":true}")
                .enablePoiFc(Boolean.TRUE)
                .enableMusicFc(Boolean.TRUE)
                .boostingTableName("boost-table")
                .hotWordsTableId("boost-id")
                .correctTableName("correct-table")
                .correctTableId("correct-id")
                .context("{\"context_type\":\"dialog_ctx\"}")
                .build();

        Map<String, Object> payload = fullClientRequestPayload(asrRequest);
        Map<String, Object> user = (Map<String, Object>) payload.get("user");
        Map<String, Object> audio = (Map<String, Object>) payload.get("audio");
        Map<String, Object> request = (Map<String, Object>) payload.get("request");
        Map<String, Object> corpus = (Map<String, Object>) request.get("corpus");

        assertEquals("user-1", user.get("uid"));
        assertEquals("device-1", user.get("did"));
        assertEquals("Linux", user.get("platform"));
        assertEquals("1.0.0", user.get("sdk_version"));
        assertEquals("2.0.0", user.get("app_version"));

        assertEquals("en-US", audio.get("language"));
        assertEquals("ogg", audio.get("format"));
        assertEquals("opus", audio.get("codec"));
        assertEquals(16000, audio.get("rate"));
        assertEquals(16, audio.get("bits"));
        assertEquals(2, audio.get("channel"));

        assertEquals(Boolean.TRUE, request.get("enable_nonstream"));
        assertEquals(Boolean.TRUE, request.get("enable_itn"));
        assertEquals(Boolean.TRUE, request.get("enable_punc"));
        assertEquals(Boolean.TRUE, request.get("enable_ddc"));
        assertEquals("traditional", request.get("output_zh_variant"));
        assertEquals(Boolean.TRUE, request.get("enable_auto_lang"));
        assertEquals(Boolean.FALSE, request.get("show_utterances"));
        assertEquals(Boolean.TRUE, request.get("show_speech_rate"));
        assertEquals(Boolean.TRUE, request.get("show_volume"));
        assertEquals(Boolean.TRUE, request.get("enable_lid"));
        assertEquals(Boolean.TRUE, request.get("enable_emotion_detection"));
        assertEquals(Boolean.TRUE, request.get("enable_gender_detection"));
        assertEquals("full", request.get("result_type"));
        assertEquals(Boolean.TRUE, request.get("enable_accelerate_text"));
        assertEquals(0.75, (Double) request.get("accelerate_score"), 0.0001);
        assertEquals(3000, request.get("vad_segment_duration"));
        assertEquals(1200, request.get("end_window_size"));
        assertEquals(1000, request.get("force_to_speech_time"));
        assertEquals("{\"system_reserved_filter\":true}", request.get("sensitive_words_filter"));
        assertEquals(Boolean.TRUE, request.get("enable_poi_fc"));
        assertEquals(Boolean.TRUE, request.get("enable_music_fc"));

        assertEquals("boost-table", corpus.get("boosting_table_name"));
        assertEquals("boost-id", corpus.get("boosting_table_id"));
        assertEquals("correct-table", corpus.get("correct_table_name"));
        assertEquals("correct-id", corpus.get("correct_table_id"));
        assertEquals("{\"context_type\":\"dialog_ctx\"}", corpus.get("context"));
    }

    @Test
    public void realtimeAsrOmitsPunctuationAndItnWhenUnset() throws Exception {
        RealTimeMessage message = startTranscriptionMessage(null, null, null, null, null, null, null);

        Map<String, Object> request = fullClientRequest(message);

        assertFalse(request.containsKey("enable_punc"));
        assertFalse(request.containsKey("enable_itn"));
    }

    @Test
    public void flashAsrOmitsPunctuationAndItnWhenEndpointDoesNotSetThem() throws Exception {
        HuoshanRealTimeAsrRequest request = new HuoshanRealTimeAsrRequest(
                AsrRequest.builder().format("wav").sampleRate(16000).content(new byte[] { 1 }).build(),
                property());

        Map<String, Object> payload = fullClientRequest(request);

        assertFalse(payload.containsKey("enable_punc"));
        assertFalse(payload.containsKey("enable_itn"));
        assertFalse(payload.containsKey("enable_ddc"));
    }

    private RealTimeMessage startTranscriptionMessage(Boolean enableNonstream, Boolean enableSpeakerInfo, Boolean enableGenderDetection,
            Integer endWindowSize, String ssdVersion) {
        return startTranscriptionMessage(Boolean.TRUE, Boolean.TRUE, enableNonstream, enableSpeakerInfo, enableGenderDetection, endWindowSize,
                ssdVersion);
    }

    private RealTimeMessage startTranscriptionMessage(Boolean enablePunctuationPrediction, Boolean enableInverseTextNormalization,
            Boolean enableNonstream, Boolean enableSpeakerInfo, Boolean enableGenderDetection, Integer endWindowSize, String ssdVersion) {
        String json = "{"
                + "\"payload\":{"
                + "\"format\":\"pcm\","
                + "\"sample_rate\":16000"
                + (enablePunctuationPrediction == null ? "" : ",\"enable_punctuation_prediction\":" + enablePunctuationPrediction)
                + (enableInverseTextNormalization == null ? "" : ",\"enable_inverse_text_normalization\":" + enableInverseTextNormalization)
                + (enableNonstream == null ? "" : ",\"enable_nonstream\":" + enableNonstream)
                + (enableSpeakerInfo == null ? "" : ",\"enable_speaker_info\":" + enableSpeakerInfo)
                + (enableGenderDetection == null ? "" : ",\"enable_gender_detection\":" + enableGenderDetection)
                + (endWindowSize == null ? "" : ",\"end_window_size\":" + endWindowSize)
                + (ssdVersion == null ? "" : ",\"ssd_version\":\"" + ssdVersion + "\"")
                + "}"
                + "}";
        return JacksonUtils.deserialize(json, RealTimeMessage.class);
    }

    private Map<String, Object> fullClientRequest(RealTimeMessage message) throws Exception {
        HuoshanRealTimeAsrRequest request = new HuoshanRealTimeAsrRequest(message, property());
        return fullClientRequest(request);
    }

    private Map<String, Object> fullClientRequest(HuoshanRealTimeAsrRequest request) throws Exception {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestId("test-request-id");
        HuoshanStreamLMAsrCallback callback = new HuoshanStreamLMAsrCallback(request, null, processData, null, null);

        Method method = HuoshanStreamLMAsrCallback.class.getDeclaredMethod("constructFullClientRequest");
        method.setAccessible(true);
        byte[] packet = (byte[]) method.invoke(callback);
        Map<String, Object> payload = JacksonUtils.toMap(decompressPayload(packet));
        return (Map<String, Object>) payload.get("request");
    }

    private Map<String, Object> fullClientRequestPayload(AsrRequest asrRequest) throws Exception {
        HuoshanRealTimeAsrRequest request = new HuoshanRealTimeAsrRequest(asrRequest, property());
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestId("test-request-id");
        HuoshanStreamLMAsrCallback callback = new HuoshanStreamLMAsrCallback(request, null, processData, null, null);

        Method method = HuoshanStreamLMAsrCallback.class.getDeclaredMethod("constructFullClientRequest");
        method.setAccessible(true);
        byte[] packet = (byte[]) method.invoke(callback);
        return JacksonUtils.toMap(decompressPayload(packet));
    }

    private HuoshanProperty property() {
        AuthorizationProperty auth = new AuthorizationProperty();
        auth.setSecret("test-token");

        HuoshanProperty property = new HuoshanProperty();
        property.setAppid("test-appid");
        property.setAuth(auth);
        property.setDeployName("test-cluster");
        return property;
    }

    private byte[] decompressPayload(byte[] packet) throws Exception {
        int payloadSize = bytesToInt(Arrays.copyOfRange(packet, 8, 12));
        byte[] compressedPayload = Arrays.copyOfRange(packet, 12, 12 + payloadSize);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressedPayload));
        byte[] buffer = new byte[1024];
        int read;
        while ((read = gzip.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        gzip.close();
        return out.toByteArray();
    }

    private int bytesToInt(byte[] src) {
        return ((src[0] & 0xFF) << 24)
                | ((src[1] & 0xff) << 16)
                | ((src[2] & 0xff) << 8)
                | ((src[3] & 0xff));
    }
}
