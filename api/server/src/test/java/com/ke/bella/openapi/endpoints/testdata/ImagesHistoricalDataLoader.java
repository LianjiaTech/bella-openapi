package com.ke.bella.openapi.endpoints.testdata;

import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Historical request data loader for image-related endpoints
 * Supports /v1/images/generations and /v1/images/edits endpoints
 */
public class ImagesHistoricalDataLoader {

    private static final String DATA_FILE = "/historical-images-requests.json";

    /**
     * Load all historical request test cases for image generation
     */
    public static List<GenerationsTestCase> loadGenerationsRequests() {
        return new ImagesHistoricalDataLoader().loadGenerationsTestData();
    }

    /**
     * Load all historical request test cases for image editing
     */
    public static List<EditsTestCase> loadEditsRequests() {
        return new ImagesHistoricalDataLoader().loadEditsTestData();
    }

    /**
     * Load generations test data
     */
    private List<GenerationsTestCase> loadGenerationsTestData() {
        try {
            InputStream inputStream = ImagesHistoricalDataLoader.class.getResourceAsStream(DATA_FILE);
            if (inputStream == null) {
                throw new RuntimeException("Cannot find test data file: " + DATA_FILE);
            }

            ImagesHistoricalData data = BaseHistoricalDataLoader.objectMapper.readValue(inputStream, ImagesHistoricalData.class);
            List<GenerationsTestCase> testCases = new ArrayList<>();

            for (BaseHistoricalDataLoader.RequestScenario scenario : data.getGenerationsRequests()) {
                testCases.add(convertToGenerationsTestCase(scenario));
            }

            return testCases;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load image generation historical request data", e);
        }
    }

    /**
     * Load edits test data
     */
    private List<EditsTestCase> loadEditsTestData() {
        try {
            InputStream inputStream = ImagesHistoricalDataLoader.class.getResourceAsStream(DATA_FILE);
            if (inputStream == null) {
                throw new RuntimeException("Cannot find test data file: " + DATA_FILE);
            }

            ImagesHistoricalData data = BaseHistoricalDataLoader.objectMapper.readValue(inputStream, ImagesHistoricalData.class);
            List<EditsTestCase> testCases = new ArrayList<>();

            for (BaseHistoricalDataLoader.RequestScenario scenario : data.getEditsRequests()) {
                testCases.add(convertToEditsTestCase(scenario));
            }

            return testCases;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load image editing historical request data", e);
        }
    }

    /**
     * Convert JSON data to image generation test case object
     */
    private GenerationsTestCase convertToGenerationsTestCase(BaseHistoricalDataLoader.RequestScenario scenario) {
        ImagesRequest request = buildImagesRequest(scenario.getRequest());
        ImagesResponse expectedResponse = buildImagesResponse(scenario.getExpectedResponse());
        ChannelDB mockChannel = buildMockChannel(scenario.getMockChannel());
        Predicate<ImagesRequest> parameterValidator = buildGenerationsParameterValidator(scenario.getParameterValidations());
        Consumer<ImagesResponse> customValidator = buildCustomValidator(scenario.getCustomValidations());

        return new GenerationsTestCase(
            scenario.getScenarioName(),
            scenario.getDescription(),
            request,
            expectedResponse,
            mockChannel,
            parameterValidator,
            customValidator
        );
    }

    /**
     * Convert JSON data to image editing test case object
     */
    private EditsTestCase convertToEditsTestCase(BaseHistoricalDataLoader.RequestScenario scenario) {
        ImagesEditRequest request = buildImagesEditRequest(scenario.getRequest());
        ImagesResponse expectedResponse = buildImagesResponse(scenario.getExpectedResponse());
        ChannelDB mockChannel = buildMockChannel(scenario.getMockChannel());
        Predicate<ImagesEditRequest> parameterValidator = buildEditsParameterValidator(scenario.getParameterValidations());
        Consumer<ImagesResponse> customValidator = buildCustomValidator(scenario.getCustomValidations());

        return new EditsTestCase(
            scenario.getScenarioName(),
            scenario.getDescription(),
            request,
            expectedResponse,
            mockChannel,
            parameterValidator,
            customValidator
        );
    }

    /**
     * Build ImagesRequest object
     */
    private ImagesRequest buildImagesRequest(Map<String, Object> requestData) {
        ImagesRequest request = new ImagesRequest();

        if (requestData.containsKey("model")) {
            request.setModel((String) requestData.get("model"));
        }
        if (requestData.containsKey("prompt")) {
            request.setPrompt((String) requestData.get("prompt"));
        }
        if (requestData.containsKey("n")) {
            request.setN((Integer) requestData.get("n"));
        }
        if (requestData.containsKey("size")) {
            request.setSize((String) requestData.get("size"));
        }
        if (requestData.containsKey("response_format")) {
            request.setResponse_format((String) requestData.get("response_format"));
        }
        if (requestData.containsKey("quality")) {
            request.setQuality((String) requestData.get("quality"));
        }
        if (requestData.containsKey("style")) {
            request.setStyle((String) requestData.get("style"));
        }
        if (requestData.containsKey("user")) {
            request.setUser((String) requestData.get("user"));
        }

        return request;
    }

    /**
     * Build ImagesEditRequest object
     */
    private ImagesEditRequest buildImagesEditRequest(Map<String, Object> requestData) {
        ImagesEditRequest request = new ImagesEditRequest();

        if (requestData.containsKey("model")) {
            request.setModel((String) requestData.get("model"));
        }
        if (requestData.containsKey("image")) {
            request.setImage_b64_json((String) requestData.get("image"));
        }
        if (requestData.containsKey("image_url")) {
            request.setImage_url((String) requestData.get("image_url"));
        }
        if (requestData.containsKey("image_b64_json")) {
            request.setImage_b64_json((String) requestData.get("image_b64_json"));
        }
        if (requestData.containsKey("mask")) {
            Object maskValue = requestData.get("mask");
        }
        if (requestData.containsKey("prompt")) {
            request.setPrompt((String) requestData.get("prompt"));
        }
        if (requestData.containsKey("n")) {
            request.setN((Integer) requestData.get("n"));
        }
        if (requestData.containsKey("size")) {
            request.setSize((String) requestData.get("size"));
        }
        if (requestData.containsKey("response_format")) {
            request.setResponse_format((String) requestData.get("response_format"));
        }
        if (requestData.containsKey("user")) {
            request.setUser((String) requestData.get("user"));
        }

        return request;
    }

    /**
     * Build ImagesResponse object
     */
    @SuppressWarnings("unchecked")
    private ImagesResponse buildImagesResponse(Map<String, Object> responseData) {
        ImagesResponse response = new ImagesResponse();

        if (responseData.containsKey("created")) {
            Long created = Long.valueOf(responseData.get("created").toString());
            response.setCreated(created);
        }

        if (responseData.containsKey("data")) {
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) responseData.get("data");
            List<ImagesResponse.ImageData> imageDataList = new ArrayList<>();

            for (Map<String, Object> imageDataMap : dataList) {
                ImagesResponse.ImageData imageData = new ImagesResponse.ImageData();

                if (imageDataMap.containsKey("url")) {
                    imageData.setUrl((String) imageDataMap.get("url"));
                }
                if (imageDataMap.containsKey("b64_json")) {
                    imageData.setB64_json((String) imageDataMap.get("b64_json"));
                }
                if (imageDataMap.containsKey("size")) {
                    imageData.setSize((String) imageDataMap.get("size"));
                }

                imageDataList.add(imageData);
            }

            response.setData(imageDataList);
        }

        return response;
    }

    /**
     * Build Mock channel object
     */
    private ChannelDB buildMockChannel(Map<String, Object> channelData) {
        ChannelDB channel = new ChannelDB();

        if (channelData.containsKey("channelInfo")) {
            channel.setChannelInfo((String) channelData.get("channelInfo"));
        }
        if (channelData.containsKey("protocol")) {
            channel.setProtocol((String) channelData.get("protocol"));
        }
        if (channelData.containsKey("url")) {
            channel.setUrl((String) channelData.get("url"));
        }

        return channel;
    }

    /**
     * Build image generation parameter validator
     */
    @SuppressWarnings("unchecked")
    private Predicate<ImagesRequest> buildGenerationsParameterValidator(List<Map<String, Object>> validations) {
        if (validations == null || validations.isEmpty()) {
            return req -> true;
        }

        return req -> {
            for (Map<String, Object> validation : validations) {
                String field = (String) validation.get("field");

                if (validation.containsKey("expectedValue")) {
                    Object expectedValue = validation.get("expectedValue");
                    if (!validateGenerationsFieldEquals(req, field, expectedValue)) {
                        return false;
                    }
                }

                if (validation.containsKey("contains")) {
                    List<String> containsValues = (List<String>) validation.get("contains");
                    if (!validateGenerationsFieldContains(req, field, containsValues)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    /**
     * Build image editing parameter validator
     */
    @SuppressWarnings("unchecked")
    private Predicate<ImagesEditRequest> buildEditsParameterValidator(List<Map<String, Object>> validations) {
        if (validations == null || validations.isEmpty()) {
            return req -> true;
        }

        return req -> {
            for (Map<String, Object> validation : validations) {
                String field = (String) validation.get("field");

                if (validation.containsKey("expectedValue")) {
                    Object expectedValue = validation.get("expectedValue");
                    if (!validateEditsFieldEquals(req, field, expectedValue)) {
                        return false;
                    }
                }

                if (validation.containsKey("contains")) {
                    List<String> containsValues = (List<String>) validation.get("contains");
                    if (!validateEditsFieldContains(req, field, containsValues)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    /**
     * Build custom validator
     */
    private Consumer<ImagesResponse> buildCustomValidator(List<Map<String, Object>> customValidations) {
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
            }
        };
    }

    // Field validation methods (keeping original logic)
    private static boolean validateGenerationsFieldEquals(ImagesRequest req, String field, Object expectedValue) {
        Object actualValue = getGenerationsFieldValue(req, field);
        return Objects.equals(expectedValue, actualValue);
    }

    private static boolean validateEditsFieldEquals(ImagesEditRequest req, String field, Object expectedValue) {
        Object actualValue = getEditsFieldValue(req, field);
        return Objects.equals(expectedValue, actualValue);
    }

    private static boolean validateGenerationsFieldContains(ImagesRequest req, String field, List<String> containsValues) {
        Object actualValue = getGenerationsFieldValue(req, field);
        if (actualValue == null) return false;

        String actualStr = actualValue.toString();
        for (String containsValue : containsValues) {
            if (!actualStr.contains(containsValue)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateEditsFieldContains(ImagesEditRequest req, String field, List<String> containsValues) {
        Object actualValue = getEditsFieldValue(req, field);
        if (actualValue == null) return false;

        String actualStr = actualValue.toString();
        for (String containsValue : containsValues) {
            if (!actualStr.contains(containsValue)) {
                return false;
            }
        }
        return true;
    }

    private static Object getGenerationsFieldValue(ImagesRequest req, String field) {
        switch (field) {
            case "model": return req.getModel();
            case "prompt": return req.getPrompt();
            case "n": return req.getN();
            case "size": return req.getSize();
            case "response_format": return req.getResponse_format();
            case "quality": return req.getQuality();
            case "style": return req.getStyle();
            case "user": return req.getUser();
            default: return null;
        }
    }

    private static Object getEditsFieldValue(ImagesEditRequest req, String field) {
        switch (field) {
            case "model": return req.getModel();
            case "image": return req.getImage_b64_json();
            case "mask": return null;
            case "prompt": return req.getPrompt();
            case "n": return req.getN();
            case "size": return req.getSize();
            case "response_format": return req.getResponse_format();
            case "user": return req.getUser();
            default: return null;
        }
    }

    /**
     * Image-related historical request data structure
     */
    @Setter
    @Getter
    public static class ImagesHistoricalData {
        private List<BaseHistoricalDataLoader.RequestScenario> generationsRequests;
        private List<BaseHistoricalDataLoader.RequestScenario> editsRequests;
    }

    /**
     * Image generation test case
     */
    @Getter
    public static class GenerationsTestCase implements BaseHistoricalDataLoader.BaseTestCase<ImagesRequest, ImagesResponse> {
        private final String scenarioName;
        private final String description;
        private final ImagesRequest request;
        private final ImagesResponse expectedResponse;
        private final ChannelDB mockChannel;
        private final Predicate<ImagesRequest> parameterValidator;
        private final Consumer<ImagesResponse> customValidator;

        public GenerationsTestCase(String scenarioName, String description,
                                 ImagesRequest request, ImagesResponse expectedResponse,
                                 ChannelDB mockChannel,
                                 Predicate<ImagesRequest> parameterValidator,
                                 Consumer<ImagesResponse> customValidator) {
            this.scenarioName = scenarioName;
            this.description = description;
            this.request = request;
            this.expectedResponse = expectedResponse;
            this.mockChannel = mockChannel;
            this.parameterValidator = parameterValidator;
            this.customValidator = customValidator;
        }
    }

    /**
     * Image editing test case
     */
    @Getter
    public static class EditsTestCase implements BaseHistoricalDataLoader.BaseTestCase<ImagesEditRequest, ImagesResponse> {
        private final String scenarioName;
        private final String description;
        private final ImagesEditRequest request;
        private final ImagesResponse expectedResponse;
        private final ChannelDB mockChannel;
        private final Predicate<ImagesEditRequest> parameterValidator;
        private final Consumer<ImagesResponse> customValidator;

        public EditsTestCase(String scenarioName, String description,
                           ImagesEditRequest request, ImagesResponse expectedResponse,
                           ChannelDB mockChannel,
                           Predicate<ImagesEditRequest> parameterValidator,
                           Consumer<ImagesResponse> customValidator) {
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