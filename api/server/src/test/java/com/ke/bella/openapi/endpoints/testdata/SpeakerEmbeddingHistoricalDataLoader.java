package com.ke.bella.openapi.endpoints.testdata;

import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingRequest;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingResponse;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Historical request data loader for speaker embedding endpoint
 * Supports /v1/audio/speaker/embedding endpoint
 */
public class SpeakerEmbeddingHistoricalDataLoader extends BaseHistoricalDataLoader<SpeakerEmbeddingRequest, SpeakerEmbeddingResponse, SpeakerEmbeddingHistoricalDataLoader.SpeakerEmbeddingTestCase> {

    private static final String[] DATA_FILES = {
        "/test-data/audio/speaker/speaker-embedding-scenarios.json"
    };

    /**
     * Load all historical request test cases for speaker embedding
     */
    public static List<SpeakerEmbeddingTestCase> loadSpeakerEmbeddingRequests() {
        SpeakerEmbeddingHistoricalDataLoader loader = new SpeakerEmbeddingHistoricalDataLoader();
        List<SpeakerEmbeddingTestCase> allTestCases = new ArrayList<>();

        for (String dataFile : DATA_FILES) {
            allTestCases.addAll(loader.loadTestData(dataFile));
        }

        return allTestCases;
    }

    @Override
    protected List<SpeakerEmbeddingTestCase> parseTestData(InputStream inputStream) throws IOException {
        SpeakerEmbeddingHistoricalData data = objectMapper.readValue(inputStream, SpeakerEmbeddingHistoricalData.class);
        List<SpeakerEmbeddingTestCase> testCases = new ArrayList<>();

        for (RequestScenario scenario : data.getSpeakerEmbeddingRequests()) {
            testCases.add(convertToTestCase(scenario));
        }

        return testCases;
    }

    @Override
    protected SpeakerEmbeddingTestCase convertToTestCase(RequestScenario scenario) {
        SpeakerEmbeddingRequest request = buildRequest(scenario.getRequest());
        SpeakerEmbeddingResponse expectedResponse = buildResponse(scenario.getExpectedResponse());
        ChannelDB mockChannel = buildMockChannel(scenario.getMockChannel());
        Predicate<SpeakerEmbeddingRequest> parameterValidator = buildParameterValidator(scenario.getParameterValidations());
        Consumer<SpeakerEmbeddingResponse> customValidator = buildCustomValidator(scenario.getCustomValidations());

        return new SpeakerEmbeddingTestCase(
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
    protected SpeakerEmbeddingRequest buildRequest(Map<String, Object> requestData) {
        SpeakerEmbeddingRequest request = new SpeakerEmbeddingRequest();

        if (requestData.containsKey("user")) {
            request.setUser(requestData.get("user").toString());
        }
        if (requestData.containsKey("model")) {
            request.setModel((String) requestData.get("model"));
        }
        if (requestData.containsKey("url")) {
            request.setUrl((String) requestData.get("url"));
        }
        if (requestData.containsKey("base64")) {
            request.setBase64((String) requestData.get("base64"));
        }
        if (requestData.containsKey("normalize")) {
            request.setNormalize((Boolean) requestData.get("normalize"));
        }
        if (requestData.containsKey("sample_rate")) {
            request.setSampleRate(((Number) requestData.get("sample_rate")).intValue());
        }
        if (requestData.containsKey("task_id")) {
            request.setTaskId((String) requestData.get("task_id"));
        }
        if (requestData.containsKey("enable_vad")) {
            request.setEnableVad((Boolean) requestData.get("enable_vad"));
        }
        if (requestData.containsKey("vad_aggressiveness")) {
            request.setVadAggressiveness(((Number) requestData.get("vad_aggressiveness")).intValue());
        }
        if (requestData.containsKey("min_speech_duration")) {
            request.setMinSpeechDuration(((Number) requestData.get("min_speech_duration")).doubleValue());
        }
        if (requestData.containsKey("max_silence_duration")) {
            request.setMaxSilenceDuration(((Number) requestData.get("max_silence_duration")).doubleValue());
        }

        return request;
    }

    @Override
    protected SpeakerEmbeddingResponse buildResponse(Map<String, Object> responseData) {
        SpeakerEmbeddingResponse response = new SpeakerEmbeddingResponse();

        if (responseData.containsKey("task")) {
            response.setTask((String) responseData.get("task"));
        }
        if (responseData.containsKey("task_id")) {
            response.setTaskId((String) responseData.get("task_id"));
        }
        if (responseData.containsKey("duration")) {
            response.setDuration(((Number) responseData.get("duration")).doubleValue());
        }
        if (responseData.containsKey("dimensions")) {
            response.setDimensions(((Number) responseData.get("dimensions")).intValue());
        }
        if (responseData.containsKey("embeddings")) {
            List<Map<String, Object>> embData = (List<Map<String, Object>>) responseData.get("embeddings");
            List<SpeakerEmbeddingResponse.Embedding> embeddings = new ArrayList<>();

            for (Map<String, Object> embMap : embData) {
                SpeakerEmbeddingResponse.Embedding embedding = new SpeakerEmbeddingResponse.Embedding();
                if (embMap.containsKey("id")) {
                    embedding.setId(((Number) embMap.get("id")).intValue());
                }
                if (embMap.containsKey("start")) {
                    embedding.setStart(((Number) embMap.get("start")).doubleValue());
                }
                if (embMap.containsKey("end")) {
                    embedding.setEnd(((Number) embMap.get("end")).doubleValue());
                }
                if (embMap.containsKey("confidence")) {
                    embedding.setConfidence(((Number) embMap.get("confidence")).doubleValue());
                }
                if (embMap.containsKey("embedding")) {
                    List<Number> embList = (List<Number>) embMap.get("embedding");
                    List<Double> doubleList = new ArrayList<>();
                    for (Number num : embList) {
                        doubleList.add(num.doubleValue());
                    }
                    embedding.setEmbedding(doubleList);
                }
                embeddings.add(embedding);
            }
            response.setEmbeddings(embeddings);
        }

        return response;
    }

    @Override
    protected Predicate<SpeakerEmbeddingRequest> buildParameterValidator(List<Map<String, Object>> validations) {
        if (validations == null || validations.isEmpty()) {
            return request -> true;
        }

        return request -> {
            for (Map<String, Object> validation : validations) {
                String field = (String) validation.get("field");
                String rule = (String) validation.get("rule");
                Object expectedValue = validation.get("expectedValue");

                switch (field) {
                    case "model":
                        if ("not_null".equals(rule) && request.getModel() == null) return false;
                        if ("equals".equals(rule) && !Objects.equals(request.getModel(), expectedValue)) return false;
                        break;
                    case "url":
                        if ("not_null".equals(rule) && request.getUrl() == null) return false;
                        break;
                    case "base64":
                        if ("not_null".equals(rule) && request.getBase64() == null) return false;
                        break;
                    case "sample_rate":
                        if ("greater_than".equals(rule) && request.getSampleRate() <= ((Number) expectedValue).intValue()) return false;
                        break;
                }
            }
            return true;
        };
    }

    @Override
    protected Consumer<SpeakerEmbeddingResponse> buildCustomValidator(List<Map<String, Object>> customValidations) {
        if (customValidations == null || customValidations.isEmpty()) {
            return response -> {};
        }

        return response -> {
            for (Map<String, Object> validation : customValidations) {
                String validationType = (String) validation.get("type");

                switch (validationType) {
                    case "dimensions_check":
                        Integer expectedDimensions = (Integer) validation.get("expectedDimensions");
                        if (expectedDimensions != null && !Objects.equals(response.getDimensions(), expectedDimensions)) {
                            throw new AssertionError(String.format("Expected dimensions %d but got %d",
                                expectedDimensions, response.getDimensions()));
                        }
                        break;
                    case "embeddings_not_empty":
                        if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
                            throw new AssertionError("Expected non-empty embeddings list");
                        }
                        break;
                    case "task_validation":
                        String expectedTask = (String) validation.get("expectedValue");
                        if (!Objects.equals(response.getTask(), expectedTask)) {
                            throw new AssertionError(String.format("Expected task '%s' but got '%s'",
                                expectedTask, response.getTask()));
                        }
                        break;
                }
            }
        };
    }

    /**
     * Container for historical speaker embedding request data from JSON
     */
    @Setter
    @Getter
    public static class SpeakerEmbeddingHistoricalData {
        private List<RequestScenario> speakerEmbeddingRequests;
    }

    /**
     * Speaker embedding test case implementation
     */
    @Getter
    public static class SpeakerEmbeddingTestCase implements BaseHistoricalDataLoader.BaseTestCase<SpeakerEmbeddingRequest, SpeakerEmbeddingResponse> {
        private final String scenarioName;
        private final String description;
        private final SpeakerEmbeddingRequest request;
        private final SpeakerEmbeddingResponse expectedResponse;
        private final ChannelDB mockChannel;
        private final Predicate<SpeakerEmbeddingRequest> parameterValidator;
        private final Consumer<SpeakerEmbeddingResponse> customValidator;

        public SpeakerEmbeddingTestCase(String scenarioName, String description,
                                      SpeakerEmbeddingRequest request, SpeakerEmbeddingResponse expectedResponse,
                                      ChannelDB mockChannel, Predicate<SpeakerEmbeddingRequest> parameterValidator,
                                      Consumer<SpeakerEmbeddingResponse> customValidator) {
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