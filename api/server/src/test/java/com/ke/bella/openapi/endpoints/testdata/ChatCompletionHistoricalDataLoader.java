package com.ke.bella.openapi.endpoints.testdata;

import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Historical request data loader for chat completion endpoint
 * Supports /v1/chat/completions endpoint
 */
public class ChatCompletionHistoricalDataLoader extends BaseHistoricalDataLoader<CompletionRequest, CompletionResponse, ChatCompletionHistoricalDataLoader.ChatCompletionTestCase> {

    private static final String[] DATA_FILES = {
        "/test-data/chat/chat-completion-scenarios.json"
    };

    /**
     * Load all historical request test cases for chat completions
     */
    public static List<ChatCompletionTestCase> loadChatCompletionRequests() {
        ChatCompletionHistoricalDataLoader loader = new ChatCompletionHistoricalDataLoader();
        List<ChatCompletionTestCase> allTestCases = new ArrayList<>();

        for (String dataFile : DATA_FILES) {
            allTestCases.addAll(loader.loadTestData(dataFile));
        }

        return allTestCases;
    }

    @Override
    protected List<ChatCompletionTestCase> parseTestData(InputStream inputStream) throws IOException {
        ChatCompletionHistoricalData data = objectMapper.readValue(inputStream, ChatCompletionHistoricalData.class);
        List<ChatCompletionTestCase> testCases = new ArrayList<>();

        for (RequestScenario scenario : data.getChatCompletionRequests()) {
            testCases.add(convertToTestCase(scenario));
        }

        return testCases;
    }

    @Override
    protected ChatCompletionTestCase convertToTestCase(RequestScenario scenario) {
        CompletionRequest request = buildRequest(scenario.getRequest());
        CompletionResponse expectedResponse = buildResponse(scenario.getExpectedResponse());
        ChannelDB mockChannel = buildMockChannel(scenario.getMockChannel());
        Predicate<CompletionRequest> parameterValidator = buildParameterValidator(scenario.getParameterValidations());
        Consumer<CompletionResponse> customValidator = buildCustomValidator(scenario.getCustomValidations());

        return new ChatCompletionTestCase(
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
    protected CompletionRequest buildRequest(Map<String, Object> requestData) {
        CompletionRequest request = new CompletionRequest();

        if (requestData.containsKey("model")) {
            request.setModel((String) requestData.get("model"));
        }
        if (requestData.containsKey("messages")) {
            List<Map<String, Object>> messagesData = (List<Map<String, Object>>) requestData.get("messages");
            List<Message> messages = new ArrayList<>();

            for (Map<String, Object> msgData : messagesData) {
                Message message = new Message();
                if (msgData.containsKey("role")) {
                    message.setRole((String) msgData.get("role"));
                }
                if (msgData.containsKey("content")) {
                    Object content = msgData.get("content");
                    if (content instanceof String) {
                        message.setContent((String) content);
                    } else if (content instanceof List) {
                        // Handle multimodal content (text + images)
                        message.setContent(content);
                    }
                }
                if (msgData.containsKey("tool_calls")) {
                    List<Map<String, Object>> toolCallsData = (List<Map<String, Object>>) msgData.get("tool_calls");
                    List<Message.ToolCall> toolCalls = new ArrayList<>();

                    for (Map<String, Object> toolCallData : toolCallsData) {
                        Message.ToolCall toolCall = new Message.ToolCall();
                        if (toolCallData.containsKey("id")) {
                            toolCall.setId((String) toolCallData.get("id"));
                        }
                        if (toolCallData.containsKey("type")) {
                            toolCall.setType((String) toolCallData.get("type"));
                        }
                        if (toolCallData.containsKey("function")) {
                            Map<String, Object> functionData = (Map<String, Object>) toolCallData.get("function");
                            Message.FunctionCall function = new Message.FunctionCall();
                            if (functionData.containsKey("name")) {
                                function.setName((String) functionData.get("name"));
                            }
                            if (functionData.containsKey("arguments")) {
                                function.setArguments((String) functionData.get("arguments"));
                            }
                            toolCall.setFunction(function);
                        }
                        toolCalls.add(toolCall);
                    }
                    message.setTool_calls(toolCalls);
                }
                if (msgData.containsKey("tool_call_id")) {
                    message.setTool_call_id((String) msgData.get("tool_call_id"));
                }
                messages.add(message);
            }
            request.setMessages(messages);
        }
        if (requestData.containsKey("tools")) {
            List<Map<String, Object>> toolsData = (List<Map<String, Object>>) requestData.get("tools");
            List<Message.Tool> tools = new ArrayList<>();

            for (Map<String, Object> toolData : toolsData) {
                Message.Tool tool = new Message.Tool();
                if (toolData.containsKey("type")) {
                    tool.setType((String) toolData.get("type"));
                }
                if (toolData.containsKey("function")) {
                    Map<String, Object> functionData = (Map<String, Object>) toolData.get("function");
                    Message.Function function = new Message.Function();
                    if (functionData.containsKey("name")) {
                        function.setName((String) functionData.get("name"));
                    }
                    if (functionData.containsKey("description")) {
                        function.setDescription((String) functionData.get("description"));
                    }
                    if (functionData.containsKey("parameters")) {
                        // Skip setting parameters for now - would need proper FunctionParameter object construction
                        // function.setParameters(functionData.get("parameters"));
                    }
                    // Note: Function class doesn't have strict field
                    // if (functionData.containsKey("strict")) {
                    //     function.setStrict((Boolean) functionData.get("strict"));
                    // }
                    tool.setFunction(function);
                }
                tools.add(tool);
            }
            request.setTools(tools);
        }
        if (requestData.containsKey("tool_choice")) {
            Object toolChoice = requestData.get("tool_choice");
            request.setTool_choice(toolChoice);
        }
        if (requestData.containsKey("temperature")) {
            request.setTemperature(((Number) requestData.get("temperature")).floatValue());
        }
        if (requestData.containsKey("top_p")) {
            request.setTop_p(((Number) requestData.get("top_p")).floatValue());
        }
        if (requestData.containsKey("n")) {
            request.setN(((Number) requestData.get("n")).intValue());
        }
        if (requestData.containsKey("stream")) {
            request.setStream((Boolean) requestData.get("stream"));
        }
        if (requestData.containsKey("stop")) {
            Object stop = requestData.get("stop");
            if (stop instanceof String) {
                request.setStop(Arrays.asList((String) stop));
            } else if (stop instanceof List) {
                request.setStop((List<String>) stop);
            }
        }
        if (requestData.containsKey("max_tokens")) {
            request.setMax_tokens(((Number) requestData.get("max_tokens")).intValue());
        }
        if (requestData.containsKey("presence_penalty")) {
            request.setPresence_penalty(((Number) requestData.get("presence_penalty")).floatValue());
        }
        if (requestData.containsKey("frequency_penalty")) {
            request.setFrequency_penalty(((Number) requestData.get("frequency_penalty")).floatValue());
        }
        if (requestData.containsKey("user")) {
            request.setUser((String) requestData.get("user"));
        }

        return request;
    }

    @Override
    protected CompletionResponse buildResponse(Map<String, Object> responseData) {
        CompletionResponse response = new CompletionResponse();

        if (responseData.containsKey("id")) {
            response.setId((String) responseData.get("id"));
        }
        if (responseData.containsKey("object")) {
            response.setObject((String) responseData.get("object"));
        }
        if (responseData.containsKey("created")) {
            response.setCreated(((Number) responseData.get("created")).longValue());
        }
        if (responseData.containsKey("model")) {
            response.setModel((String) responseData.get("model"));
        }
        if (responseData.containsKey("system_fingerprint")) {
            response.setSystem_fingerprint((String) responseData.get("system_fingerprint"));
        }
        if (responseData.containsKey("choices")) {
            List<Map<String, Object>> choicesData = (List<Map<String, Object>>) responseData.get("choices");
            List<CompletionResponse.Choice> choices = new ArrayList<>();

            for (Map<String, Object> choiceData : choicesData) {
                CompletionResponse.Choice choice = new CompletionResponse.Choice();
                if (choiceData.containsKey("index")) {
                    choice.setIndex(((Number) choiceData.get("index")).intValue());
                }
                if (choiceData.containsKey("message")) {
                    Map<String, Object> messageData = (Map<String, Object>) choiceData.get("message");
                    Message message = new Message();
                    if (messageData.containsKey("role")) {
                        message.setRole((String) messageData.get("role"));
                    }
                    if (messageData.containsKey("content")) {
                        message.setContent((String) messageData.get("content"));
                    }
                    if (messageData.containsKey("reasoning_content")) {
                        message.setReasoning_content((String) messageData.get("reasoning_content"));
                    }
                    if (messageData.containsKey("tool_calls")) {
                        List<Map<String, Object>> toolCallsData = (List<Map<String, Object>>) messageData.get("tool_calls");
                        List<Message.ToolCall> toolCalls = new ArrayList<>();

                        for (Map<String, Object> toolCallData : toolCallsData) {
                            Message.ToolCall toolCall = new Message.ToolCall();
                            if (toolCallData.containsKey("id")) {
                                toolCall.setId((String) toolCallData.get("id"));
                            }
                            if (toolCallData.containsKey("type")) {
                                toolCall.setType((String) toolCallData.get("type"));
                            }
                            if (toolCallData.containsKey("function")) {
                                Map<String, Object> functionData = (Map<String, Object>) toolCallData.get("function");
                                Message.FunctionCall function = new Message.FunctionCall();
                                if (functionData.containsKey("name")) {
                                    function.setName((String) functionData.get("name"));
                                }
                                if (functionData.containsKey("arguments")) {
                                    function.setArguments((String) functionData.get("arguments"));
                                }
                                toolCall.setFunction(function);
                            }
                            toolCalls.add(toolCall);
                        }
                        message.setTool_calls(toolCalls);
                    }
                    choice.setMessage(message);
                }
                if (choiceData.containsKey("finish_reason")) {
                    choice.setFinish_reason((String) choiceData.get("finish_reason"));
                }
                choices.add(choice);
            }
            response.setChoices(choices);
        }
        if (responseData.containsKey("usage")) {
            Map<String, Object> usageData = (Map<String, Object>) responseData.get("usage");
            CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
            if (usageData.containsKey("prompt_tokens")) {
                usage.setPrompt_tokens(((Number) usageData.get("prompt_tokens")).intValue());
            }
            if (usageData.containsKey("completion_tokens")) {
                usage.setCompletion_tokens(((Number) usageData.get("completion_tokens")).intValue());
            }
            if (usageData.containsKey("total_tokens")) {
                usage.setTotal_tokens(((Number) usageData.get("total_tokens")).intValue());
            }
            response.setUsage(usage);
        }

        return response;
    }

    @Override
    protected Predicate<CompletionRequest> buildParameterValidator(List<Map<String, Object>> validations) {
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
                    case "messages":
                        if ("not_empty".equals(rule) && (request.getMessages() == null || request.getMessages().isEmpty())) return false;
                        break;
                    case "tools":
                        if ("not_empty".equals(rule) && (request.getTools() == null || request.getTools().isEmpty())) return false;
                        break;
                    case "stream":
                        if ("equals".equals(rule) && !Objects.equals(request.isStream(), expectedValue)) return false;
                        break;
                    case "temperature":
                        if ("greater_than".equals(rule) && request.getTemperature() <= ((Number) expectedValue).doubleValue()) return false;
                        break;
                }
            }
            return true;
        };
    }

    @Override
    protected Consumer<CompletionResponse> buildCustomValidator(List<Map<String, Object>> customValidations) {
        if (customValidations == null || customValidations.isEmpty()) {
            return response -> {};
        }

        return response -> {
            for (Map<String, Object> validation : customValidations) {
                String validationType = (String) validation.get("type");

                switch (validationType) {
                    case "choices_not_empty":
                        if (response.getChoices() == null || response.getChoices().isEmpty()) {
                            throw new AssertionError("Expected non-empty choices list");
                        }
                        break;
                    case "usage_validation":
                        if (response.getUsage() == null) {
                            throw new AssertionError("Expected usage information");
                        }
                        if (response.getUsage().getTotal_tokens() <= 0) {
                            throw new AssertionError("Expected positive total tokens");
                        }
                        break;
                    case "tool_calls_validation":
                        if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                            CompletionResponse.Choice firstChoice = response.getChoices().get(0);
                            if (firstChoice.getMessage() != null &&
                                (firstChoice.getMessage().getTool_calls() == null || firstChoice.getMessage().getTool_calls().isEmpty())) {
                                throw new AssertionError("Expected tool calls in response");
                            }
                        }
                        break;
                    case "multimodal_validation":
                        // For multimodal requests, ensure response handles image input properly
                        if (response.getChoices() == null || response.getChoices().isEmpty()) {
                            throw new AssertionError("Expected response for multimodal input");
                        }
                        break;
                    case "stream_response_validation":
                        if (!"chat.completion.chunk".equals(response.getObject())) {
                            throw new AssertionError("Expected streaming response object type");
                        }
                        break;
                    case "basic_response_validation":
                        if (response.getId() == null || response.getId().isEmpty()) {
                            throw new AssertionError("Expected response ID");
                        }
                        break;
                }
            }
        };
    }

    /**
     * Container for historical chat completion request data from JSON
     */
    @Setter
    @Getter
    public static class ChatCompletionHistoricalData {
        private List<RequestScenario> chatCompletionRequests;
    }

    /**
     * Chat completion test case implementation
     */
    @Getter
    public static class ChatCompletionTestCase implements BaseHistoricalDataLoader.BaseTestCase<CompletionRequest, CompletionResponse> {
        private final String scenarioName;
        private final String description;
        private final CompletionRequest request;
        private final CompletionResponse expectedResponse;
        private final ChannelDB mockChannel;
        private final Predicate<CompletionRequest> parameterValidator;
        private final Consumer<CompletionResponse> customValidator;

        public ChatCompletionTestCase(String scenarioName, String description,
                                    CompletionRequest request, CompletionResponse expectedResponse,
                                    ChannelDB mockChannel, Predicate<CompletionRequest> parameterValidator,
                                    Consumer<CompletionResponse> customValidator) {
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