package com.ke.bella.openapi.endpoints.testdata;

import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.ke.bella.openapi.protocol.asr.flash.FlashAsrResponse;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Historical request data loader for Flash ASR endpoint
 * Supports /v1/audio/asr/flash endpoint
 */
public class FlashAsrHistoricalDataLoader extends BaseHistoricalDataLoader<AsrRequest, FlashAsrHistoricalDataLoader.FlashAsrResponse, FlashAsrHistoricalDataLoader.FlashAsrTestCase> {

    private static final String[] DATA_FILES = {
        "/test-data/audio/asr/flash-asr-scenarios.json"
    };

    /**
     * Load all historical request test cases for Flash ASR
     */
    public static List<FlashAsrTestCase> loadFlashAsrRequests() {
        FlashAsrHistoricalDataLoader loader = new FlashAsrHistoricalDataLoader();
        List<FlashAsrTestCase> allTestCases = new ArrayList<>();

        for (String dataFile : DATA_FILES) {
            allTestCases.addAll(loader.loadTestData(dataFile));
        }

        return allTestCases;
    }

    @Override
    protected List<FlashAsrTestCase> parseTestData(InputStream inputStream) throws IOException {
        FlashAsrHistoricalData data = objectMapper.readValue(inputStream, FlashAsrHistoricalData.class);
        List<FlashAsrTestCase> testCases = new ArrayList<>();

        for (RequestScenario scenario : data.getFlashAsrRequests()) {
            testCases.add(convertToTestCase(scenario));
        }

        return testCases;
    }

    @Override
    protected FlashAsrTestCase convertToTestCase(RequestScenario scenario) {
        AsrRequest request = buildRequest(scenario.getRequest());
        FlashAsrResponse expectedResponse = buildResponse(scenario.getExpectedResponse());
        ChannelDB mockChannel = buildMockChannel(scenario.getMockChannel());
        Predicate<AsrRequest> parameterValidator = buildParameterValidator(scenario.getParameterValidations());
        Consumer<FlashAsrResponse> customValidator = buildCustomValidator(scenario.getCustomValidations());

        return new FlashAsrTestCase(
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
    protected AsrRequest buildRequest(Map<String, Object> requestData) {
        AsrRequest request = AsrRequest.builder().build();

        if (requestData.containsKey("model")) {
            request.setModel((String) requestData.get("model"));
        }
        if (requestData.containsKey("format")) {
            request.setFormat((String) requestData.get("format"));
        }
        if (requestData.containsKey("sample_rate")) {
            Object sampleRateValue = requestData.get("sample_rate");
            if (sampleRateValue instanceof Number) {
                request.setSampleRate(((Number) sampleRateValue).intValue());
            }
        }
        if (requestData.containsKey("max_sentence_silence")) {
            Object maxSentenceSilenceValue = requestData.get("max_sentence_silence");
            if (maxSentenceSilenceValue instanceof Number) {
                request.setMaxSentenceSilence(((Number) maxSentenceSilenceValue).intValue());
            }
        }
        if (requestData.containsKey("hot_words")) {
            request.setHotWords((String) requestData.get("hot_words"));
        }
        if (requestData.containsKey("hot_words_table_id")) {
            request.setHotWordsTableId((String) requestData.get("hot_words_table_id"));
        }
        if (requestData.containsKey("audio_content")) {
            String audioContent = (String) requestData.get("audio_content");
            request.setContent(audioContent.getBytes());
        }

        return request;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected FlashAsrResponse buildResponse(Map<String, Object> responseData) {
        FlashAsrResponse response = new FlashAsrResponse();

        if (responseData.containsKey("task_id")) {
            response.setTaskId((String) responseData.get("task_id"));
        }
        if (responseData.containsKey("user")) {
            response.setUser((String) responseData.get("user"));
        }
        if (responseData.containsKey("flash_result")) {
            Map<String, Object> flashResultData = (Map<String, Object>) responseData.get("flash_result");
            FlashResult flashResult = new FlashResult();

            if (flashResultData.containsKey("duration")) {
                Object durationValue = flashResultData.get("duration");
                if (durationValue instanceof Number) {
                    flashResult.setDuration(((Number) durationValue).intValue());
                }
            }

            if (flashResultData.containsKey("sentences")) {
                List<Map<String, Object>> sentencesData = (List<Map<String, Object>>) flashResultData.get("sentences");
                List<Sentence> sentences = new ArrayList<>();

                for (Map<String, Object> sentenceData : sentencesData) {
                    Sentence sentence = new Sentence();
                    if (sentenceData.containsKey("text")) {
                        sentence.setText((String) sentenceData.get("text"));
                    }
                    if (sentenceData.containsKey("begin_time")) {
                        Object beginTimeValue = sentenceData.get("begin_time");
                        if (beginTimeValue instanceof Number) {
                            sentence.setBeginTime(((Number) beginTimeValue).longValue());
                        }
                    }
                    if (sentenceData.containsKey("end_time")) {
                        Object endTimeValue = sentenceData.get("end_time");
                        if (endTimeValue instanceof Number) {
                            sentence.setEndTime(((Number) endTimeValue).longValue());
                        }
                    }
                    sentences.add(sentence);
                }
                flashResult.setSentences(sentences);
            }

            response.setFlashResult(flashResult);
        }

        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Predicate<AsrRequest> buildParameterValidator(List<Map<String, Object>> validations) {
        if (validations == null || validations.isEmpty()) {
            return req -> true;
        }

        return req -> {
            for (Map<String, Object> validation : validations) {
                String field = (String) validation.get("field");

                if (validation.containsKey("expectedValue")) {
                    Object expectedValue = validation.get("expectedValue");
                    if (!validateFlashAsrFieldEquals(req, field, expectedValue)) {
                        return false;
                    }
                }

                if (validation.containsKey("contains")) {
                    List<String> containsValues = (List<String>) validation.get("contains");
                    if (!validateFlashAsrFieldContains(req, field, containsValues)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    @Override
    protected Consumer<FlashAsrResponse> buildCustomValidator(List<Map<String, Object>> customValidations) {
        if (customValidations == null || customValidations.isEmpty()) {
            return response -> {};
        }

        return response -> {
            for (Map<String, Object> validation : customValidations) {
                String type = (String) validation.get("type");
                String description = (String) validation.get("description");

                if ("durationRange".equals(type)) {
                    Integer minValue = (Integer) validation.get("minValue");
                    Integer maxValue = (Integer) validation.get("maxValue");
                    int actualDuration = response.getFlashResult().getDuration();
                    if (minValue != null && actualDuration < minValue) {
                        throw new AssertionError(description + " expected minimum:<" + minValue + "> but was:<" + actualDuration + ">");
                    }
                    if (maxValue != null && actualDuration > maxValue) {
                        throw new AssertionError(description + " expected maximum:<" + maxValue + "> but was:<" + actualDuration + ">");
                    }
                }

                if ("sentenceCount".equals(type)) {
                    Integer minValue = (Integer) validation.get("minValue");
                    Integer maxValue = (Integer) validation.get("maxValue");
                    int actualCount = response.getFlashResult().getSentences().size();
                    if (minValue != null && actualCount < minValue) {
                        throw new AssertionError(description + " expected minimum sentence count:<" + minValue + "> but was:<" + actualCount + ">");
                    }
                    if (maxValue != null && actualCount > maxValue) {
                        throw new AssertionError(description + " expected maximum sentence count:<" + maxValue + "> but was:<" + actualCount + ">");
                    }
                }
            }
        };
    }

    /**
     * Validate Flash ASR field value equality
     */
    private static boolean validateFlashAsrFieldEquals(AsrRequest req, String field, Object expectedValue) {
        Object actualValue = getFlashAsrFieldValue(req, field);
        return Objects.equals(expectedValue, actualValue);
    }

    /**
     * Validate Flash ASR field contains specified values
     */
    private static boolean validateFlashAsrFieldContains(AsrRequest req, String field, List<String> containsValues) {
        Object actualValue = getFlashAsrFieldValue(req, field);
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
     * Get Flash ASR field value
     */
    private static Object getFlashAsrFieldValue(AsrRequest req, String field) {
        switch (field) {
            case "model": return req.getModel();
            case "format": return req.getFormat();
            case "sample_rate": return req.getSampleRate();
            case "max_sentence_silence": return req.getMaxSentenceSilence();
            case "hot_words": return req.getHotWords();
            case "hot_words_table_id": return req.getHotWordsTableId();
            default: return null;
        }
    }

    /**
     * Flash ASR response data structure for testing
     */
    @Setter
    @Getter
    public static class FlashAsrResponse {
        private String taskId;
        private String user;
        private FlashResult flashResult;
    }

    /**
     * Flash result data structure for testing
     */
    @Setter
    @Getter
    public static class FlashResult {
        private int duration;
        private List<Sentence> sentences;
    }

    /**
     * Sentence data structure for testing
     */
    @Setter
    @Getter
    public static class Sentence {
        private String text;
        private long beginTime;
        private long endTime;
    }

    /**
     * Flash ASR-related historical request data structure
     */
    @Setter
    @Getter
    public static class FlashAsrHistoricalData {
        private List<RequestScenario> flashAsrRequests;
    }

    /**
     * Flash ASR test case
     */
    @Getter
    public static class FlashAsrTestCase implements BaseHistoricalDataLoader.BaseTestCase<AsrRequest, FlashAsrResponse> {
        private final String scenarioName;
        private final String description;
        private final AsrRequest request;
        private final FlashAsrResponse expectedResponse;
        private final ChannelDB mockChannel;
        private final Predicate<AsrRequest> parameterValidator;
        private final Consumer<FlashAsrResponse> customValidator;

        public FlashAsrTestCase(String scenarioName, String description,
                               AsrRequest request, FlashAsrResponse expectedResponse,
                               ChannelDB mockChannel,
                               Predicate<AsrRequest> parameterValidator,
                               Consumer<FlashAsrResponse> customValidator) {
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