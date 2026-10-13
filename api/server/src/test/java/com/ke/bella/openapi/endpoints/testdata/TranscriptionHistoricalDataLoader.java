package com.ke.bella.openapi.endpoints.testdata;

import com.ke.bella.openapi.protocol.asr.AudioTranscriptionRequest.AudioTranscriptionReq;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionResponse.AudioTranscriptionResp;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Historical request data loader for audio transcription endpoint
 * Supports /v1/audio/transcriptions/file endpoint
 */
public class TranscriptionHistoricalDataLoader extends BaseHistoricalDataLoader<AudioTranscriptionReq, AudioTranscriptionResp, TranscriptionHistoricalDataLoader.TranscriptionTestCase> {

    private static final String[] DATA_FILES = {
        "/test-data/audio/transcription/transcription-scenarios.json"
    };

    /**
     * Load all historical request test cases for transcription
     */
    public static List<TranscriptionTestCase> loadTranscriptionRequests() {
        TranscriptionHistoricalDataLoader loader = new TranscriptionHistoricalDataLoader();
        List<TranscriptionTestCase> allTestCases = new ArrayList<>();

        for (String dataFile : DATA_FILES) {
            allTestCases.addAll(loader.loadTestData(dataFile));
        }

        return allTestCases;
    }

    @Override
    protected List<TranscriptionTestCase> parseTestData(InputStream inputStream) throws IOException {
        TranscriptionHistoricalData data = objectMapper.readValue(inputStream, TranscriptionHistoricalData.class);
        List<TranscriptionTestCase> testCases = new ArrayList<>();

        for (RequestScenario scenario : data.getTranscriptionRequests()) {
            testCases.add(convertToTestCase(scenario));
        }

        return testCases;
    }

    @Override
    protected TranscriptionTestCase convertToTestCase(RequestScenario scenario) {
        AudioTranscriptionReq request = buildRequest(scenario.getRequest());
        AudioTranscriptionResp expectedResponse = buildResponse(scenario.getExpectedResponse());
        ChannelDB mockChannel = buildMockChannel(scenario.getMockChannel());
        Predicate<AudioTranscriptionReq> parameterValidator = buildParameterValidator(scenario.getParameterValidations());
        Consumer<AudioTranscriptionResp> customValidator = buildCustomValidator(scenario.getCustomValidations());

        // Task ID is already set from expectedResponse in buildResponse method

        return new TranscriptionTestCase(
            scenario.getScenarioName(),
            scenario.getDescription(),
            request,
            expectedResponse,
            mockChannel,
            parameterValidator,
            customValidator
        );
    }

    @Override
    protected AudioTranscriptionReq buildRequest(Map<String, Object> requestData) {
        AudioTranscriptionReq request = new AudioTranscriptionReq();

        // Basic required fields
        if (requestData.containsKey("url")) {
            request.setUrl((String) requestData.get("url"));
        }
        if (requestData.containsKey("model")) {
            request.setModel((String) requestData.get("model"));
        }
        if (requestData.containsKey("user")) {
            request.setUser((String) requestData.get("user"));
        }
        if (requestData.containsKey("callback_url")) {
            request.setCallbackUrl((String) requestData.get("callback_url"));
        }

        // Common fields
        if (requestData.containsKey("channel_number")) {
            request.setChannelNumber(getIntValue(requestData.get("channel_number")));
        }
        if (requestData.containsKey("speaker_diarization")) {
            request.setSpeakerDiarization((Boolean) requestData.get("speaker_diarization"));
        }
        if (requestData.containsKey("speaker_number")) {
            request.setSpeakerNumber(getIntValue(requestData.get("speaker_number")));
        }
        if (requestData.containsKey("hot_word")) {
            request.setHotWord((String) requestData.get("hot_word"));
        }

        // Language and processing fields
        if (requestData.containsKey("language")) {
            request.setLanguage((String) requestData.get("language"));
        }
        if (requestData.containsKey("candidate")) {
            request.setCandidate(getIntValue(requestData.get("candidate")));
        }
        if (requestData.containsKey("audio_mode")) {
            request.setAudioMode((String) requestData.get("audio_mode"));
        }
        if (requestData.containsKey("standard_wav")) {
            request.setStandardWav(getIntValue(requestData.get("standard_wav")));
        }
        if (requestData.containsKey("language_type")) {
            request.setLanguageType(getIntValue(requestData.get("language_type")));
        }
        if (requestData.containsKey("trans_mode")) {
            request.setTransMode(getIntValue(requestData.get("trans_mode")));
        }

        // Processing switches
        if (requestData.containsKey("eng_smoothproc")) {
            request.setEngSmoothproc((Boolean) requestData.get("eng_smoothproc"));
        }
        if (requestData.containsKey("eng_collogproc")) {
            request.setEngCollogproc((Boolean) requestData.get("eng_collogproc"));
        }
        if (requestData.containsKey("eng_vad_mdn")) {
            request.setEngVadMdn(getIntValue(requestData.get("eng_vad_mdn")));
        }
        if (requestData.containsKey("eng_vad_margin")) {
            request.setEngVadMargin(getIntValue(requestData.get("eng_vad_margin")));
        }
        if (requestData.containsKey("eng_rlang")) {
            request.setEngRlang(getIntValue(requestData.get("eng_rlang")));
        }

        // Tencent specific fields
        if (requestData.containsKey("vocab_id")) {
            request.setVocabId((String) requestData.get("vocab_id"));
        }

        // Custom/Self-developed fields
        if (requestData.containsKey("sample_rate")) {
            request.setSampleRate(getIntValue(requestData.get("sample_rate")));
        }
        if (requestData.containsKey("enable_words")) {
            request.setEnableWords((Boolean) requestData.get("enable_words"));
        }
        if (requestData.containsKey("enable_vad")) {
            request.setEnableVad((Boolean) requestData.get("enable_vad"));
        }
        if (requestData.containsKey("chunk_length")) {
            request.setChunkLength(getIntValue(requestData.get("chunk_length")));
        }
        if (requestData.containsKey("enable_semantic_sentence_detection")) {
            request.setEnableSemanticSentenceDetection((Boolean) requestData.get("enable_semantic_sentence_detection"));
        }
        if (requestData.containsKey("enable_punctuation_prediction")) {
            request.setEnablePunctuationPrediction((Boolean) requestData.get("enable_punctuation_prediction"));
        }
        if (requestData.containsKey("max_end_silence")) {
            request.setMaxEndSilence(getIntValue(requestData.get("max_end_silence")));
        }

        // Huoshan specific fields
        if (requestData.containsKey("enable_itn")) {
            request.setEnableItn((Boolean) requestData.get("enable_itn"));
        }
        if (requestData.containsKey("enable_ddc")) {
            request.setEnableDdc((Boolean) requestData.get("enable_ddc"));
        }
        if (requestData.containsKey("enable_channel_split")) {
            request.setEnableChannelSplit((Boolean) requestData.get("enable_channel_split"));
        }
        if (requestData.containsKey("show_utterances")) {
            request.setShowUtterances((Boolean) requestData.get("show_utterances"));
        }
        if (requestData.containsKey("vad_segment")) {
            request.setVadSegment((Boolean) requestData.get("vad_segment"));
        }
        if (requestData.containsKey("sensitive_words_filter")) {
            request.setSensitiveWordsFilter((String) requestData.get("sensitive_words_filter"));
        }
        if (requestData.containsKey("boosting_table_name")) {
            request.setBoostingTableName((String) requestData.get("boosting_table_name"));
        }

        return request;
    }

    @Override
    protected AudioTranscriptionResp buildResponse(Map<String, Object> responseData) {
        AudioTranscriptionResp response = new AudioTranscriptionResp();

        if (responseData.containsKey("task_id")) {
            response.setTaskId((String) responseData.get("task_id"));
        }

        return response;
    }

    @Override
    protected Predicate<AudioTranscriptionReq> buildParameterValidator(List<Map<String, Object>> validations) {
        if (validations == null || validations.isEmpty()) {
            return req -> true;
        }

        return req -> {
            for (Map<String, Object> validation : validations) {
                String field = (String) validation.get("field");

                if (validation.containsKey("expectedValue")) {
                    Object expectedValue = validation.get("expectedValue");
                    if (!validateTranscriptionFieldEquals(req, field, expectedValue)) {
                        return false;
                    }
                }

                if (validation.containsKey("contains")) {
                    @SuppressWarnings("unchecked")
                    List<String> containsValues = (List<String>) validation.get("contains");
                    if (!validateTranscriptionFieldContains(req, field, containsValues)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    @Override
    protected Consumer<AudioTranscriptionResp> buildCustomValidator(List<Map<String, Object>> customValidations) {
        if (customValidations == null || customValidations.isEmpty()) {
            return response -> {};
        }

        return response -> {
            for (Map<String, Object> validation : customValidations) {
                String type = (String) validation.get("type");
                String description = (String) validation.get("description");

                if ("taskIdFormat".equals(type)) {
                    String expectedPattern = (String) validation.get("expectedPattern");
                    if (!response.getTaskId().matches(expectedPattern)) {
                        throw new AssertionError(description + " expected pattern:<" + expectedPattern + "> but was:<" + response.getTaskId() + ">");
                    }
                }

                if ("taskIdNotNull".equals(type)) {
                    if (response.getTaskId() == null || response.getTaskId().isEmpty()) {
                        throw new AssertionError(description + " task_id should not be null or empty");
                    }
                }
            }
        };
    }

    /**
     * Helper method to safely convert Object to int
     */
    private int getIntValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Validate transcription field value equality
     */
    private static boolean validateTranscriptionFieldEquals(AudioTranscriptionReq req, String field, Object expectedValue) {
        Object actualValue = getTranscriptionFieldValue(req, field);
        return Objects.equals(expectedValue, actualValue);
    }

    /**
     * Validate transcription field contains specified values
     */
    private static boolean validateTranscriptionFieldContains(AudioTranscriptionReq req, String field, List<String> containsValues) {
        Object actualValue = getTranscriptionFieldValue(req, field);
        if (actualValue == null) return false;

        String actualStr = actualValue.toString();
        for (String containsValue : containsValues) {
            if (!actualStr.contains(containsValue)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get transcription field value by field name
     */
    private static Object getTranscriptionFieldValue(AudioTranscriptionReq req, String field) {
        switch (field) {
            case "url": return req.getUrl();
            case "model": return req.getModel();
            case "user": return req.getUser();
            case "callback_url": return req.getCallbackUrl();
            case "channel_number": return req.getChannelNumber();
            case "speaker_diarization": return req.isSpeakerDiarization();
            case "speaker_number": return req.getSpeakerNumber();
            case "hot_word": return req.getHotWord();
            case "language": return req.getLanguage();
            case "candidate": return req.getCandidate();
            case "audio_mode": return req.getAudioMode();
            case "standard_wav": return req.getStandardWav();
            case "language_type": return req.getLanguageType();
            case "trans_mode": return req.getTransMode();
            case "eng_smoothproc": return req.isEngSmoothproc();
            case "eng_collogproc": return req.isEngCollogproc();
            case "eng_vad_mdn": return req.getEngVadMdn();
            case "eng_vad_margin": return req.getEngVadMargin();
            case "eng_rlang": return req.getEngRlang();
            case "vocab_id": return req.getVocabId();
            case "sample_rate": return req.getSampleRate();
            case "enable_words": return req.isEnableWords();
            case "enable_vad": return req.isEnableVad();
            case "chunk_length": return req.getChunkLength();
            case "enable_semantic_sentence_detection": return req.isEnableSemanticSentenceDetection();
            case "enable_punctuation_prediction": return req.isEnablePunctuationPrediction();
            case "max_end_silence": return req.getMaxEndSilence();
            case "enable_itn": return req.isEnableItn();
            case "enable_ddc": return req.isEnableDdc();
            case "enable_channel_split": return req.isEnableChannelSplit();
            case "show_utterances": return req.isShowUtterances();
            case "vad_segment": return req.isVadSegment();
            case "sensitive_words_filter": return req.getSensitiveWordsFilter();
            case "boosting_table_name": return req.getBoostingTableName();
            default: return null;
        }
    }

    /**
     * Transcription-related historical request data structure
     */
    @Setter
    @Getter
    public static class TranscriptionHistoricalData {
        private List<RequestScenario> transcriptionRequests;
    }

    /**
     * Transcription test case
     */
    @Getter
    public static class TranscriptionTestCase implements BaseHistoricalDataLoader.BaseTestCase<AudioTranscriptionReq, AudioTranscriptionResp> {
        private final String scenarioName;
        private final String description;
        private final AudioTranscriptionReq request;
        private final AudioTranscriptionResp expectedResponse;
        private final ChannelDB mockChannel;
        private final Predicate<AudioTranscriptionReq> parameterValidator;
        private final Consumer<AudioTranscriptionResp> customValidator;

        public TranscriptionTestCase(String scenarioName, String description,
                                   AudioTranscriptionReq request, AudioTranscriptionResp expectedResponse,
                                   ChannelDB mockChannel,
                                   Predicate<AudioTranscriptionReq> parameterValidator,
                                   Consumer<AudioTranscriptionResp> customValidator) {
            this.scenarioName = scenarioName;
            this.description = description;
            this.request = request;
            this.expectedResponse = expectedResponse;
            this.mockChannel = mockChannel;
            this.parameterValidator = parameterValidator;
            this.customValidator = customValidator;
        }
    }
}