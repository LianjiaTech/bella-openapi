package com.ke.bella.openapi.endpoints.testdata;

import com.ke.bella.openapi.protocol.tts.TtsRequest;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Historical request data loader for speech/TTS endpoint
 * Supports /v1/audio/speech endpoint
 */
public class SpeechHistoricalDataLoader extends BaseHistoricalDataLoader<TtsRequest, SpeechHistoricalDataLoader.SpeechResponse, SpeechHistoricalDataLoader.SpeechTestCase> {

    private static final String[] DATA_FILES = {
        "/test-data/audio/tts/speech-scenarios.json"
    };

    /**
     * Load all historical request test cases for speech/TTS
     */
    public static List<SpeechTestCase> loadSpeechRequests() {
        SpeechHistoricalDataLoader loader = new SpeechHistoricalDataLoader();
        List<SpeechTestCase> allTestCases = new ArrayList<>();

        for (String dataFile : DATA_FILES) {
            allTestCases.addAll(loader.loadTestData(dataFile));
        }

        return allTestCases;
    }

    @Override
    protected List<SpeechTestCase> parseTestData(InputStream inputStream) throws IOException {
        SpeechHistoricalData data = objectMapper.readValue(inputStream, SpeechHistoricalData.class);
        List<SpeechTestCase> testCases = new ArrayList<>();

        for (RequestScenario scenario : data.getSpeechRequests()) {
            testCases.add(convertToTestCase(scenario));
        }

        return testCases;
    }

    @Override
    protected SpeechTestCase convertToTestCase(RequestScenario scenario) {
        TtsRequest request = buildRequest(scenario.getRequest());
        SpeechResponse expectedResponse = buildResponse(scenario.getExpectedResponse());
        ChannelDB mockChannel = buildMockChannel(scenario.getMockChannel());
        Predicate<TtsRequest> parameterValidator = buildParameterValidator(scenario.getParameterValidations());
        Consumer<SpeechResponse> customValidator = buildCustomValidator(scenario.getCustomValidations());

        return new SpeechTestCase(
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
    protected TtsRequest buildRequest(Map<String, Object> requestData) {
        TtsRequest request = new TtsRequest();

        if (requestData.containsKey("user")) {
            request.setUser(requestData.get("user").toString());
        }
        if (requestData.containsKey("model")) {
            request.setModel((String) requestData.get("model"));
        }
        if (requestData.containsKey("input")) {
            request.setInput((String) requestData.get("input"));
        }
        if (requestData.containsKey("voice")) {
            request.setVoice((String) requestData.get("voice"));
        }
        if (requestData.containsKey("response_format")) {
            request.setResponseFormat((String) requestData.get("response_format"));
        }
        if (requestData.containsKey("speed")) {
            Object speedValue = requestData.get("speed");
            if (speedValue instanceof Number) {
                request.setSpeed(((Number) speedValue).doubleValue());
            }
        }
        if (requestData.containsKey("sample_rate")) {
            Object sampleRateValue = requestData.get("sample_rate");
            if (sampleRateValue instanceof Number) {
                request.setSampleRate(((Number) sampleRateValue).intValue());
            }
        }
        if (requestData.containsKey("stream")) {
            request.setStream((Boolean) requestData.get("stream"));
        }

        return request;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected SpeechResponse buildResponse(Map<String, Object> responseData) {
        SpeechResponse response = new SpeechResponse();

        if (responseData.containsKey("contentType")) {
            response.setContentType((String) responseData.get("contentType"));
        }
        if (responseData.containsKey("dataLength")) {
            Object dataLengthValue = responseData.get("dataLength");
            if (dataLengthValue instanceof Number) {
                response.setDataLength(((Number) dataLengthValue).intValue());
            }
        }

        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Predicate<TtsRequest> buildParameterValidator(List<Map<String, Object>> validations) {
        if (validations == null || validations.isEmpty()) {
            return req -> true;
        }

        return req -> {
            for (Map<String, Object> validation : validations) {
                String field = (String) validation.get("field");

                if (validation.containsKey("expectedValue")) {
                    Object expectedValue = validation.get("expectedValue");
                    if (!validateSpeechFieldEquals(req, field, expectedValue)) {
                        return false;
                    }
                }

                if (validation.containsKey("contains")) {
                    List<String> containsValues = (List<String>) validation.get("contains");
                    if (!validateSpeechFieldContains(req, field, containsValues)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    @Override
    protected Consumer<SpeechResponse> buildCustomValidator(List<Map<String, Object>> customValidations) {
        if (customValidations == null || customValidations.isEmpty()) {
            return response -> {};
        }

        return response -> {
            for (Map<String, Object> validation : customValidations) {
                String type = (String) validation.get("type");
                String description = (String) validation.get("description");

                if ("dataLengthRange".equals(type)) {
                    Integer minLength = (Integer) validation.get("minValue");
                    Integer maxLength = (Integer) validation.get("maxValue");
                    if (minLength != null && response.getDataLength() < minLength) {
                        throw new AssertionError(description + " expected minimum:<" + minLength + "> but was:<" + response.getDataLength() + ">");
                    }
                    if (maxLength != null && response.getDataLength() > maxLength) {
                        throw new AssertionError(description + " expected maximum:<" + maxLength + "> but was:<" + response.getDataLength() + ">");
                    }
                }

                if ("contentType".equals(type)) {
                    String expectedContentType = (String) validation.get("expectedValue");
                    if (!Objects.equals(expectedContentType, response.getContentType())) {
                        throw new AssertionError(description + " expected:<" + expectedContentType + "> but was:<" + response.getContentType() + ">");
                    }
                }
            }
        };
    }

    /**
     * Validate speech field value equality
     */
    private static boolean validateSpeechFieldEquals(TtsRequest req, String field, Object expectedValue) {
        Object actualValue = getSpeechFieldValue(req, field);
        return Objects.equals(expectedValue, actualValue);
    }

    /**
     * Validate speech field contains specified values
     */
    private static boolean validateSpeechFieldContains(TtsRequest req, String field, List<String> containsValues) {
        Object actualValue = getSpeechFieldValue(req, field);
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
     * Get speech field value
     */
    private static Object getSpeechFieldValue(TtsRequest req, String field) {
        switch (field) {
            case "user": return req.getUser();
            case "model": return req.getModel();
            case "input": return req.getInput();
            case "voice": return req.getVoice();
            case "response_format": return req.getResponseFormat();
            case "speed": return req.getSpeed();
            case "sample_rate": return req.getSampleRate();
            case "stream": return req.isStream();
            default: return null;
        }
    }

    /**
     * Speech response data structure for testing
     */
    @Setter
    @Getter
    public static class SpeechResponse {
        private String contentType;
        private Integer dataLength;
    }

    /**
     * Speech-related historical request data structure
     */
    @Setter
    @Getter
    public static class SpeechHistoricalData {
        private List<RequestScenario> speechRequests;
    }

    /**
     * Speech test case
     */
    @Getter
    public static class SpeechTestCase implements BaseHistoricalDataLoader.BaseTestCase<TtsRequest, SpeechResponse> {
        private final String scenarioName;
        private final String description;
        private final TtsRequest request;
        private final SpeechResponse expectedResponse;
        private final ChannelDB mockChannel;
        private final Predicate<TtsRequest> parameterValidator;
        private final Consumer<SpeechResponse> customValidator;

        public SpeechTestCase(String scenarioName, String description,
                             TtsRequest request, SpeechResponse expectedResponse,
                             ChannelDB mockChannel,
                             Predicate<TtsRequest> parameterValidator,
                             Consumer<SpeechResponse> customValidator) {
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