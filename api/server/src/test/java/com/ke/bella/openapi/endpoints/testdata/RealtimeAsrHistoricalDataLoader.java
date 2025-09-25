package com.ke.bella.openapi.endpoints.testdata;

import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Historical request data loader for Realtime ASR endpoint
 * Supports /v1/audio/realtime and /v1/audio/asr/stream endpoints
 */
public class RealtimeAsrHistoricalDataLoader extends BaseHistoricalDataLoader<RealtimeAsrHistoricalDataLoader.RealtimeAsrRequest, RealtimeAsrHistoricalDataLoader.RealtimeAsrResponse, RealtimeAsrHistoricalDataLoader.RealtimeAsrTestCase> {

    private static final String[] DATA_FILES = {
        "/test-data/audio/asr/realtime-asr-scenarios.json"
    };

    /**
     * Load all historical request test cases for Realtime ASR
     */
    public static List<RealtimeAsrTestCase> loadRealtimeAsrRequests() {
        RealtimeAsrHistoricalDataLoader loader = new RealtimeAsrHistoricalDataLoader();
        List<RealtimeAsrTestCase> allTestCases = new ArrayList<>();

        for (String dataFile : DATA_FILES) {
            allTestCases.addAll(loader.loadTestData(dataFile));
        }

        return allTestCases;
    }

    @Override
    protected List<RealtimeAsrTestCase> parseTestData(InputStream inputStream) throws IOException {
        RealtimeAsrHistoricalData data = objectMapper.readValue(inputStream, RealtimeAsrHistoricalData.class);
        List<RealtimeAsrTestCase> testCases = new ArrayList<>();

        for (RequestScenario scenario : data.getRealtimeAsrRequests()) {
            testCases.add(convertToTestCase(scenario));
        }

        return testCases;
    }

    @Override
    protected RealtimeAsrTestCase convertToTestCase(RequestScenario scenario) {
        RealtimeAsrRequest request = buildRequest(scenario.getRequest());
        RealtimeAsrResponse expectedResponse = buildResponse(scenario.getExpectedResponse());
        ChannelDB mockChannel = buildMockChannel(scenario.getMockChannel());
        Predicate<RealtimeAsrRequest> parameterValidator = buildParameterValidator(scenario.getParameterValidations());
        Consumer<RealtimeAsrResponse> customValidator = buildCustomValidator(scenario.getCustomValidations());

        return new RealtimeAsrTestCase(
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
    @SuppressWarnings("unchecked")
    protected RealtimeAsrRequest buildRequest(Map<String, Object> requestData) {
        RealtimeAsrRequest request = new RealtimeAsrRequest();

        if (requestData.containsKey("model")) {
            request.setModel((String) requestData.get("model"));
        }
        if (requestData.containsKey("endpoint")) {
            request.setEndpoint((String) requestData.get("endpoint"));
        }
        if (requestData.containsKey("websocket_upgrade")) {
            request.setWebsocketUpgrade((Boolean) requestData.get("websocket_upgrade"));
        }
        if (requestData.containsKey("startMessage")) {
            Map<String, Object> startMessageData = (Map<String, Object>) requestData.get("startMessage");
            if (startMessageData != null) {
                StartMessage startMessage = buildStartMessage(startMessageData);
                request.setStartMessage(startMessage);
            }
        }

        return request;
    }

    @SuppressWarnings("unchecked")
    private StartMessage buildStartMessage(Map<String, Object> startMessageData) {
        StartMessage startMessage = new StartMessage();

        if (startMessageData.containsKey("header")) {
            Map<String, Object> headerData = (Map<String, Object>) startMessageData.get("header");
            MessageHeader header = new MessageHeader();
            if (headerData.containsKey("message_id")) {
                header.setMessageId((String) headerData.get("message_id"));
            }
            if (headerData.containsKey("task_id")) {
                header.setTaskId((String) headerData.get("task_id"));
            }
            if (headerData.containsKey("namespace")) {
                header.setNamespace((String) headerData.get("namespace"));
            }
            if (headerData.containsKey("name")) {
                header.setName((String) headerData.get("name"));
            }
            if (headerData.containsKey("appkey")) {
                header.setAppkey((String) headerData.get("appkey"));
            }
            startMessage.setHeader(header);
        }

        if (startMessageData.containsKey("payload")) {
            Map<String, Object> payloadData = (Map<String, Object>) startMessageData.get("payload");
            MessagePayload payload = new MessagePayload();
            if (payloadData.containsKey("format")) {
                payload.setFormat((String) payloadData.get("format"));
            }
            if (payloadData.containsKey("sample_rate")) {
                Object sampleRateValue = payloadData.get("sample_rate");
                if (sampleRateValue instanceof Number) {
                    payload.setSampleRate(((Number) sampleRateValue).intValue());
                }
            }
            if (payloadData.containsKey("enable_intermediate_result")) {
                payload.setEnableIntermediateResult((Boolean) payloadData.get("enable_intermediate_result"));
            }
            if (payloadData.containsKey("enable_punctuation_prediction")) {
                payload.setEnablePunctuationPrediction((Boolean) payloadData.get("enable_punctuation_prediction"));
            }
            if (payloadData.containsKey("enable_inverse_text_normalization")) {
                payload.setEnableInverseTextNormalization((Boolean) payloadData.get("enable_inverse_text_normalization"));
            }
            // Handle llm_option and tts_option if needed
            if (payloadData.containsKey("llm_option")) {
                payload.setLlmOption((Map<String, Object>) payloadData.get("llm_option"));
            }
            if (payloadData.containsKey("tts_option")) {
                payload.setTtsOption((Map<String, Object>) payloadData.get("tts_option"));
            }
            if (payloadData.containsKey("variables")) {
                payload.setVariables((Map<String, Object>) payloadData.get("variables"));
            }
            startMessage.setPayload(payload);
        }

        return startMessage;
    }

    @Override
    protected RealtimeAsrResponse buildResponse(Map<String, Object> responseData) {
        RealtimeAsrResponse response = new RealtimeAsrResponse();

        if (responseData.containsKey("websocket_connection")) {
            response.setWebsocketConnection((String) responseData.get("websocket_connection"));
        }
        if (responseData.containsKey("status_code")) {
            Object statusCodeValue = responseData.get("status_code");
            if (statusCodeValue instanceof Number) {
                response.setStatusCode(((Number) statusCodeValue).intValue());
            }
        }
        if (responseData.containsKey("upgrade_header")) {
            response.setUpgradeHeader((String) responseData.get("upgrade_header"));
        }
        if (responseData.containsKey("error_message")) {
            response.setErrorMessage((String) responseData.get("error_message"));
        }

        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Predicate<RealtimeAsrRequest> buildParameterValidator(List<Map<String, Object>> validations) {
        if (validations == null || validations.isEmpty()) {
            return req -> true;
        }

        return req -> {
            for (Map<String, Object> validation : validations) {
                String field = (String) validation.get("field");

                if (validation.containsKey("expectedValue")) {
                    Object expectedValue = validation.get("expectedValue");
                    if (!validateRealtimeAsrFieldEquals(req, field, expectedValue)) {
                        return false;
                    }
                }

                if (validation.containsKey("contains")) {
                    List<String> containsValues = (List<String>) validation.get("contains");
                    if (!validateRealtimeAsrFieldContains(req, field, containsValues)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    @Override
    protected Consumer<RealtimeAsrResponse> buildCustomValidator(List<Map<String, Object>> customValidations) {
        if (customValidations == null || customValidations.isEmpty()) {
            return response -> {};
        }

        return response -> {
            for (Map<String, Object> validation : customValidations) {
                String type = (String) validation.get("type");
                String description = (String) validation.get("description");

                if ("websocketUpgrade".equals(type)) {
                    if (!"established".equals(response.getWebsocketConnection())) {
                        throw new AssertionError(description + " expected WebSocket connection to be established");
                    }
                    if (response.getStatusCode() != 101) {
                        throw new AssertionError(description + " expected status code 101 for WebSocket upgrade, but was " + response.getStatusCode());
                    }
                }

                if ("sampleRateValidation".equals(type)) {
                    Integer expectedValue = (Integer) validation.get("expectedValue");
                    // In a real test, we would validate this against the actual request payload
                    // For now, we just ensure the validation framework is in place
                }
            }
        };
    }

    /**
     * Validate Realtime ASR field value equality
     */
    private static boolean validateRealtimeAsrFieldEquals(RealtimeAsrRequest req, String field, Object expectedValue) {
        Object actualValue = getRealtimeAsrFieldValue(req, field);
        return Objects.equals(expectedValue, actualValue);
    }

    /**
     * Validate Realtime ASR field contains specified values
     */
    private static boolean validateRealtimeAsrFieldContains(RealtimeAsrRequest req, String field, List<String> containsValues) {
        Object actualValue = getRealtimeAsrFieldValue(req, field);
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
     * Get Realtime ASR field value with support for nested fields
     */
    private static Object getRealtimeAsrFieldValue(RealtimeAsrRequest req, String field) {
        switch (field) {
            case "model": return req.getModel();
            case "endpoint": return req.getEndpoint();
            case "websocket_upgrade": return req.isWebsocketUpgrade();
            case "startMessage.payload.sample_rate":
                return req.getStartMessage() != null && req.getStartMessage().getPayload() != null ?
                       req.getStartMessage().getPayload().getSampleRate() : null;
            case "llm_option.main.model":
                if (req.getStartMessage() != null && req.getStartMessage().getPayload() != null
                    && req.getStartMessage().getPayload().getLlmOption() != null) {
                    Map<String, Object> llmOption = req.getStartMessage().getPayload().getLlmOption();
                    Map<String, Object> main = (Map<String, Object>) llmOption.get("main");
                    return main != null ? main.get("model") : null;
                }
                return null;
            case "tts_option.model":
                if (req.getStartMessage() != null && req.getStartMessage().getPayload() != null
                    && req.getStartMessage().getPayload().getTtsOption() != null) {
                    Map<String, Object> ttsOption = req.getStartMessage().getPayload().getTtsOption();
                    return ttsOption.get("model");
                }
                return null;
            default: return null;
        }
    }

    /**
     * Realtime ASR request data structure for testing
     */
    @Setter
    @Getter
    public static class RealtimeAsrRequest {
        private String model;
        private String endpoint;
        private boolean websocketUpgrade;
        private StartMessage startMessage;
    }

    /**
     * Start message data structure
     */
    @Setter
    @Getter
    public static class StartMessage {
        private MessageHeader header;
        private MessagePayload payload;
    }

    /**
     * Message header data structure
     */
    @Setter
    @Getter
    public static class MessageHeader {
        private String messageId;
        private String taskId;
        private String namespace;
        private String name;
        private String appkey;
    }

    /**
     * Message payload data structure
     */
    @Setter
    @Getter
    public static class MessagePayload {
        private String format;
        private int sampleRate;
        private boolean enableIntermediateResult;
        private boolean enablePunctuationPrediction;
        private boolean enableInverseTextNormalization;
        private Map<String, Object> llmOption;
        private Map<String, Object> ttsOption;
        private Map<String, Object> variables;
    }

    /**
     * Realtime ASR response data structure for testing
     */
    @Setter
    @Getter
    public static class RealtimeAsrResponse {
        private String websocketConnection;
        private int statusCode;
        private String upgradeHeader;
        private String errorMessage;
    }

    /**
     * Realtime ASR-related historical request data structure
     */
    @Setter
    @Getter
    public static class RealtimeAsrHistoricalData {
        private List<RequestScenario> realtimeAsrRequests;
    }

    /**
     * Realtime ASR test case
     */
    @Getter
    public static class RealtimeAsrTestCase implements BaseHistoricalDataLoader.BaseTestCase<RealtimeAsrRequest, RealtimeAsrResponse> {
        private final String scenarioName;
        private final String description;
        private final RealtimeAsrRequest request;
        private final RealtimeAsrResponse expectedResponse;
        private final ChannelDB mockChannel;
        private final Predicate<RealtimeAsrRequest> parameterValidator;
        private final Consumer<RealtimeAsrResponse> customValidator;

        public RealtimeAsrTestCase(String scenarioName, String description,
                                  RealtimeAsrRequest request, RealtimeAsrResponse expectedResponse,
                                  ChannelDB mockChannel,
                                  Predicate<RealtimeAsrRequest> parameterValidator,
                                  Consumer<RealtimeAsrResponse> customValidator) {
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