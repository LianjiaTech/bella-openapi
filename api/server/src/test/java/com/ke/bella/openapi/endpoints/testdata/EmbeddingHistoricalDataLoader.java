package com.ke.bella.openapi.endpoints.testdata;

import com.ke.bella.openapi.protocol.embedding.EmbeddingRequest;
import com.ke.bella.openapi.protocol.embedding.EmbeddingResponse;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Historical request data loader for embedding endpoint
 * Supports /v1/embeddings endpoint
 */
public class EmbeddingHistoricalDataLoader extends BaseHistoricalDataLoader<EmbeddingRequest, EmbeddingResponse, EmbeddingHistoricalDataLoader.EmbeddingTestCase> {

    private static final String[] DATA_FILES = {
        "/test-data/embeddings/openai-embedding-scenarios.json"
    };

    /**
     * Load all historical request test cases for embeddings
     */
    public static List<EmbeddingTestCase> loadEmbeddingRequests() {
        EmbeddingHistoricalDataLoader loader = new EmbeddingHistoricalDataLoader();
        List<EmbeddingTestCase> allTestCases = new ArrayList<>();

        for (String dataFile : DATA_FILES) {
            allTestCases.addAll(loader.loadTestData(dataFile));
        }

        return allTestCases;
    }

    @Override
    protected List<EmbeddingTestCase> parseTestData(InputStream inputStream) throws IOException {
        EmbeddingHistoricalData data = objectMapper.readValue(inputStream, EmbeddingHistoricalData.class);
        List<EmbeddingTestCase> testCases = new ArrayList<>();

        for (RequestScenario scenario : data.getEmbeddingRequests()) {
            testCases.add(convertToTestCase(scenario));
        }

        return testCases;
    }

    @Override
    protected EmbeddingTestCase convertToTestCase(RequestScenario scenario) {
        EmbeddingRequest request = buildRequest(scenario.getRequest());
        EmbeddingResponse expectedResponse = buildResponse(scenario.getExpectedResponse());
        ChannelDB mockChannel = buildMockChannel(scenario.getMockChannel());
        Predicate<EmbeddingRequest> parameterValidator = buildParameterValidator(scenario.getParameterValidations());
        Consumer<EmbeddingResponse> customValidator = buildCustomValidator(scenario.getCustomValidations());

        return new EmbeddingTestCase(
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
    protected EmbeddingRequest buildRequest(Map<String, Object> requestData) {
        EmbeddingRequest request = new EmbeddingRequest();

        if (requestData.containsKey("model")) {
            request.setModel((String) requestData.get("model"));
        }
        if (requestData.containsKey("input")) {
            request.setInput(requestData.get("input"));
        }
        if (requestData.containsKey("encoding_format")) {
            request.setEncodingFormat((String) requestData.get("encoding_format"));
        }
        if (requestData.containsKey("dimensions")) {
            request.setDimensions((Integer) requestData.get("dimensions"));
        }
        if (requestData.containsKey("user")) {
            request.setUser((String) requestData.get("user"));
        }

        return request;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected EmbeddingResponse buildResponse(Map<String, Object> responseData) {
        EmbeddingResponse response = new EmbeddingResponse();

        if (responseData.containsKey("object")) {
            response.setObject((String) responseData.get("object"));
        }
        if (responseData.containsKey("model")) {
            response.setModel((String) responseData.get("model"));
        }

        if (responseData.containsKey("data")) {
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) responseData.get("data");
            List<EmbeddingResponse.EmbeddingData> embeddingDataList = new ArrayList<>();

            for (Map<String, Object> embeddingDataMap : dataList) {
                EmbeddingResponse.EmbeddingData embeddingData = new EmbeddingResponse.EmbeddingData();

                if (embeddingDataMap.containsKey("object")) {
                    embeddingData.setObject((String) embeddingDataMap.get("object"));
                }
                if (embeddingDataMap.containsKey("embedding")) {
                    embeddingData.setEmbedding(embeddingDataMap.get("embedding"));
                }
                if (embeddingDataMap.containsKey("index")) {
                    embeddingData.setIndex((Integer) embeddingDataMap.get("index"));
                }

                embeddingDataList.add(embeddingData);
            }

            response.setData(embeddingDataList);
        }

        if (responseData.containsKey("usage")) {
            Map<String, Object> usageData = (Map<String, Object>) responseData.get("usage");
            EmbeddingResponse.TokenUsage usage = new EmbeddingResponse.TokenUsage();

            if (usageData.containsKey("prompt_tokens")) {
                usage.setPrompt_tokens((Integer) usageData.get("prompt_tokens"));
            }
            if (usageData.containsKey("total_tokens")) {
                usage.setTotal_tokens((Integer) usageData.get("total_tokens"));
            }

            response.setUsage(usage);
        }

        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Predicate<EmbeddingRequest> buildParameterValidator(List<Map<String, Object>> validations) {
        if (validations == null || validations.isEmpty()) {
            return req -> true;
        }

        return req -> {
            for (Map<String, Object> validation : validations) {
                String field = (String) validation.get("field");

                if (validation.containsKey("expectedValue")) {
                    Object expectedValue = validation.get("expectedValue");
                    if (!validateEmbeddingFieldEquals(req, field, expectedValue)) {
                        return false;
                    }
                }

                if (validation.containsKey("contains")) {
                    List<String> containsValues = (List<String>) validation.get("contains");
                    if (!validateEmbeddingFieldContains(req, field, containsValues)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    @Override
    protected Consumer<EmbeddingResponse> buildCustomValidator(List<Map<String, Object>> customValidations) {
        if (customValidations == null || customValidations.isEmpty()) {
            return response -> {};
        }

        return response -> {
            for (Map<String, Object> validation : customValidations) {
                String type = (String) validation.get("type");
                String description = (String) validation.get("description");

                if ("responseCount".equals(type)) {
                    Integer expectedCount = (Integer) validation.get("expectedValue");
                    if (response.getData().size() != expectedCount) {
                        throw new AssertionError(description + " expected:<" + expectedCount + "> but was:<" + response.getData().size() + ">");
                    }
                }

                if ("embeddingDimensions".equals(type)) {
                    Integer expectedDimensions = (Integer) validation.get("expectedValue");
                    for (EmbeddingResponse.EmbeddingData data : response.getData()) {
                        if (data.getEmbedding() instanceof List) {
                            List<?> embedding = (List<?>) data.getEmbedding();
                            if (embedding.size() != expectedDimensions) {
                                throw new AssertionError(description + " expected:<" + expectedDimensions + "> but was:<" + embedding.size() + ">");
                            }
                        }
                    }
                }
            }
        };
    }

    /**
     * Validate embedding field value equality
     */
    private static boolean validateEmbeddingFieldEquals(EmbeddingRequest req, String field, Object expectedValue) {
        Object actualValue = getEmbeddingFieldValue(req, field);
        return Objects.equals(expectedValue, actualValue);
    }

    /**
     * Validate embedding field contains specified values
     */
    private static boolean validateEmbeddingFieldContains(EmbeddingRequest req, String field, List<String> containsValues) {
        Object actualValue = getEmbeddingFieldValue(req, field);
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
     * Get embedding field value
     */
    private static Object getEmbeddingFieldValue(EmbeddingRequest req, String field) {
        switch (field) {
            case "model": return req.getModel();
            case "input": return req.getInput();
            case "encoding_format": return req.getEncodingFormat();
            case "dimensions": return req.getDimensions();
            case "user": return req.getUser();
            default: return null;
        }
    }

    /**
     * Embedding-related historical request data structure
     */
    @Setter
    @Getter
    public static class EmbeddingHistoricalData {
        private List<RequestScenario> embeddingRequests;
    }

    /**
     * Embedding test case
     */
    @Getter
    public static class EmbeddingTestCase implements BaseHistoricalDataLoader.BaseTestCase<EmbeddingRequest, EmbeddingResponse> {
        private final String scenarioName;
        private final String description;
        private final EmbeddingRequest request;
        private final EmbeddingResponse expectedResponse;
        private final ChannelDB mockChannel;
        private final Predicate<EmbeddingRequest> parameterValidator;
        private final Consumer<EmbeddingResponse> customValidator;

        public EmbeddingTestCase(String scenarioName, String description,
                               EmbeddingRequest request, EmbeddingResponse expectedResponse,
                               ChannelDB mockChannel,
                               Predicate<EmbeddingRequest> parameterValidator,
                               Consumer<EmbeddingResponse> customValidator) {
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
