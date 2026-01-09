package com.ke.bella.openapi.mock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.utils.JacksonUtils;

/**
 * Utility class for generating function calls and tool calls from tools
 * definitions.
 */
public class FunctionCallGenerator {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Generates Python function calls from a list of Message.Tool objects.
     * 
     * @param tools The list of Message.Tool objects
     * 
     * @return A Python code string with function calls for each tool
     */
    public static String generatePythonCode(List<Message.Tool> tools, boolean parallel) {
        if(tools == null || tools.isEmpty()) {
            return "";
        }

        StringBuilder pythonCode = new StringBuilder("\n\n```python\n");

        // Process each tool in the list
        for (Message.Tool tool : tools) {
            if("function".equals(tool.getType()) && tool.getFunction() != null) {
                Message.Function function = tool.getFunction();

                // Extract function name
                String functionName = function.getName();
                if(functionName == null || functionName.isEmpty()) {
                    continue;
                }

                // Generate function call with parameters
                StringBuilder callBuilder = new StringBuilder();
                callBuilder.append(functionName).append("(");

                Message.Function.FunctionParameter parameters = function.getParameters();
                if(parameters != null && parameters.getProperties() != null) {
                    try {
                        JsonNode propertiesNode = MAPPER.valueToTree(parameters.getProperties());
                        List<String> paramStrings = new ArrayList<>();

                        Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            String paramName = entry.getKey();
                            JsonNode paramDef = entry.getValue();

                            String paramValue = generateDefaultValue(paramDef);
                            paramStrings.add(String.format("%s=%s", paramName, paramValue));
                        }

                        callBuilder.append(String.join(", ", paramStrings));
                    } catch (Exception e) {
                        // If there's an error, continue with empty parameters
                    }
                }

                callBuilder.append(")");
                pythonCode.append(callBuilder).append("\n");
                if(parallel) {
                    pythonCode.append(callBuilder).append("\n");
                }
            }
        }

        pythonCode.append("```");
        return pythonCode.toString();
    }

    /**
     * Generates Message.ToolCalls from a list of Message.Tool objects.
     * 
     * @param tools The list of Message.Tool objects
     * 
     * @return A list of ToolCall objects
     */
    public static List<Message.ToolCall> generateToolCalls(List<Message.Tool> tools, boolean parallel) {
        List<Message.ToolCall> toolCalls = new ArrayList<>();

        if(tools == null || tools.isEmpty()) {
            return toolCalls;
        }

        int index = 0;
        // Process each tool in the list
        for (Message.Tool tool : tools) {
            if("function".equals(tool.getType()) && tool.getFunction() != null) {
                Message.Function function = tool.getFunction();

                // Extract function name
                String functionName = function.getName();
                if(functionName == null || functionName.isEmpty()) {
                    continue;
                }

                // Build arguments object
                StringBuilder argsBuilder = new StringBuilder("{");

                Message.Function.FunctionParameter parameters = function.getParameters();
                if(parameters != null && parameters.getProperties() != null) {
                    try {
                        JsonNode propertiesNode = MAPPER.valueToTree(parameters.getProperties());
                        List<String> paramStrings = new ArrayList<>();

                        Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> entry = fields.next();
                            String paramName = entry.getKey();
                            JsonNode paramDef = entry.getValue();

                            String paramValue = generateDefaultValueForJson(paramDef);
                            paramStrings.add(String.format("\"%s\": \"%s\"", paramName, paramValue));
                        }

                        argsBuilder.append(String.join(", ", paramStrings));
                    } catch (Exception e) {
                        // If there's an error, continue with empty arguments
                    }
                }

                argsBuilder.append("}");

                // Create ToolCall object
                Message.ToolCall toolCall = Message.ToolCall.builder()
                        .id("call_" + UUID.randomUUID())
                        .type("function")
                        .index(index++)
                        .function(Message.FunctionCall.builder()
                                .name(functionName)
                                .arguments(argsBuilder.toString())
                                .build())
                        .build();

                toolCalls.add(toolCall);

                if(parallel) {
                    Message.ToolCall toolCall1 = Message.ToolCall.builder()
                            .id("call_" + UUID.randomUUID())
                            .type("function")
                            .index(index++)
                            .function(Message.FunctionCall.builder()
                                    .name(functionName)
                                    .arguments(argsBuilder.toString())
                                    .build())
                            .build();
                    toolCalls.add(toolCall1);
                }
            }
        }

        return toolCalls;
    }

    /**
     * Generates a default value for a parameter based on its JSON schema
     * definition.
     * Used for Python code generation.
     */
    private static String generateDefaultValue(JsonNode paramDef) {
        if(paramDef == null || !paramDef.has("type")) {
            return "None";
        }

        String type = paramDef.get("type").asText();

        // Handle enum values first
        if(paramDef.has("enum") && paramDef.get("enum").isArray() &&
                paramDef.get("enum").size() > 0) {
            JsonNode firstEnum = paramDef.get("enum").get(0);
            if(firstEnum.isTextual()) {
                return "\"" + firstEnum.asText() + "\"";
            } else {
                return firstEnum.toString();
            }
        }

        // Generate value based on type
        switch (type.toLowerCase()) {
        case "string":
            return "\"sample text\"";
        case "integer":
            return "0";
        case "number":
            return "0.0";
        case "boolean":
            return "False";
        case "array":
            return "[]";
        case "object":
            return "{}";
        default:
            return "None";
        }
    }

    /**
     * Generates a default value for a parameter without quotes for JSON
     * arguments.
     * Used for ToolCall generation.
     */
    private static String generateDefaultValueForJson(JsonNode paramDef) {
        if(paramDef == null || !paramDef.has("type")) {
            return "null";
        }

        String type = paramDef.get("type").asText();

        // Handle enum values first
        if(paramDef.has("enum") && paramDef.get("enum").isArray() &&
                paramDef.get("enum").size() > 0) {
            JsonNode firstEnum = paramDef.get("enum").get(0);
            return firstEnum.asText();
        }

        // Generate value based on type
        switch (type.toLowerCase()) {
        case "string":
            return "sample text";
        case "integer":
            return "0";
        case "number":
            return "0.0";
        case "boolean":
            return "false";
        case "array":
            return "[]";
        case "object":
            return "{}";
        default:
            return "null";
        }
    }

    /**
     * Main method for testing.
     */
    public static void main(String[] args) {
        // Create a sample list of tools for testing
        List<Message.Tool> tools = new ArrayList<>();

        // Create a sample tool
        Message.Tool tool = new Message.Tool();
        tool.setType("function");

        // Create a sample function
        Message.Function function = new Message.Function();
        function.setName("web_search_tavily");
        function.setDescription("一个用于谷歌搜索并提取片段和网页的工具");

        // Create sample parameters
        Message.Function.FunctionParameter parameters = new Message.Function.FunctionParameter();
        parameters.setType("object");

        // Create required parameters
        List<String> required = new ArrayList<>();
        required.add("query");
        parameters.setRequired(required);

        // Create properties (this would normally be a Map or ObjectNode)
        // For testing purposes, we'll create a simple property structure
        try {
            String propertiesJson = "{\"query\": {\"title\": \"Query\", \"description\": \"查询语句\", \"type\": \"string\"}}";
            JsonNode propertiesNode = MAPPER.readTree(propertiesJson);
            parameters.setProperties(propertiesNode);
        } catch (Exception e) {
            System.err.println("Error creating test properties: " + e.getMessage());
        }

        function.setParameters(parameters);
        tool.setFunction(function);
        tools.add(tool);

        // Test generating Python code
        System.out.println("Generated Python code:");
        System.out.println(generatePythonCode(tools, true));

        // Test generating ToolCalls
        System.out.println("\nGenerated ToolCalls:");
        List<Message.ToolCall> toolCalls = generateToolCalls(tools, true);
        for (Message.ToolCall toolCall : toolCalls) {
            System.out.println("Tool Call:");
            System.out.println(JacksonUtils.serialize(toolCall));
        }
    }
}
