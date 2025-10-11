package com.ke.bella.openapi.endpoints.testdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base class for historical request data loaders
 * Provides common functionality for loading and parsing historical test data
 *
 * @param <REQUEST> Request type (e.g., EmbeddingRequest, ImagesRequest)
 * @param <RESPONSE> Response type (e.g., EmbeddingResponse, ImagesResponse)
 * @param <TEST_CASE> Test case type (e.g., EmbeddingTestCase, GenerationsTestCase)
 */
public abstract class BaseHistoricalDataLoader<REQUEST, RESPONSE, TEST_CASE> {

    protected static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Load historical test data from JSON file
     * @param dataFileName JSON file name in resources
     * @return List of test cases
     */
    protected List<TEST_CASE> loadTestData(String dataFileName) {
        try {
            InputStream inputStream = this.getClass().getResourceAsStream(dataFileName);
            if (inputStream == null) {
                throw new RuntimeException("Cannot find test data file: " + dataFileName);
            }

            return parseTestData(inputStream);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load historical request data from " + dataFileName, e);
        }
    }

    /**
     * Parse JSON input stream into test cases
     * @param inputStream JSON data input stream
     * @return List of test cases
     * @throws IOException if parsing fails
     */
    protected abstract List<TEST_CASE> parseTestData(InputStream inputStream) throws IOException;

    /**
     * Convert JSON scenario to test case object
     * @param scenario JSON scenario data
     * @return Test case object
     */
    protected abstract TEST_CASE convertToTestCase(RequestScenario scenario);

    /**
     * Build request object from JSON data
     * @param requestData JSON request data
     * @return Request object
     */
    protected abstract REQUEST buildRequest(Map<String, Object> requestData);

    /**
     * Build response object from JSON data
     * @param responseData JSON response data
     * @return Response object
     */
    protected abstract RESPONSE buildResponse(Map<String, Object> responseData);

    /**
     * Build parameter validator from JSON validation rules
     * @param validations JSON validation rules
     * @return Parameter validator predicate
     */
    protected abstract Predicate<REQUEST> buildParameterValidator(List<Map<String, Object>> validations);

    /**
     * Build custom validator from JSON validation rules
     * @param customValidations JSON custom validation rules
     * @return Custom validator consumer
     */
    protected abstract Consumer<RESPONSE> buildCustomValidator(List<Map<String, Object>> customValidations);

    /**
     * Build Mock channel object from JSON data
     * @param channelData JSON channel data
     * @return Mock channel object
     */
    protected ChannelDB buildMockChannel(Map<String, Object> channelData) {
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
     * Common request scenario structure from JSON
     */
    @Setter
    @Getter
    public static class RequestScenario {
        private String scenarioName;
        private String description;
        private Map<String, Object> request;
        private Map<String, Object> expectedResponse;
        private Map<String, Object> mockChannel;
        private List<Map<String, Object>> parameterValidations;
        private List<Map<String, Object>> customValidations;
    }

    /**
     * Base test case interface
     */
    public interface BaseTestCase<REQUEST, RESPONSE> {
        String getScenarioName();
        String getDescription();
        REQUEST getRequest();
        RESPONSE getExpectedResponse();
        ChannelDB getMockChannel();
        Predicate<REQUEST> getParameterValidator();
        Consumer<RESPONSE> getCustomValidator();
    }
}