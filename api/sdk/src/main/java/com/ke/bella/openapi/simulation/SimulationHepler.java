package com.ke.bella.openapi.simulation;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.CompletionResponse.Choice;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.Message.Function;
import com.ke.bella.openapi.utils.Renders;

public class SimulationHepler {

    public static CompletionResponse.Choice parse(String reasoning, String content) throws IOException {
        PythonFuncCallListener sfc = new PythonFuncCallListener(null);
        PythonFuncCallParser parser = new PythonFuncCallParser(new StringReader(content), sfc);
        parser.parse();

        return sfc.getToolcalls().isEmpty() ? CompletionResponse.assistantMessageChoice(reasoning, sfc.getBuffer().toString())
                : CompletionResponse.toolcallChoice(reasoning, sfc.getToolcalls());
    }

    public static void parse(Reader cs, FunctionCallListener callback) throws IOException {
        PythonFuncCallListener sfc = new PythonFuncCallListener(callback);
        PythonFuncCallParser parser = new PythonFuncCallParser(cs, sfc);
        parser.parse();
    }

    /**
     * request rewrite.
     *
     * @param req
     *
     * @return null if can not rewrite.
     */
    @SuppressWarnings("deprecation")
    public static CompletionRequest rewrite(CompletionRequest req) {
        if(CollectionUtils.isEmpty(req.getFunctions()) && CollectionUtils.isEmpty(req.getTools())) {
            return null;
        }

        if(CollectionUtils.isEmpty(req.getMessages())) {
            return null;
        }

        StringBuilder agentInfo = new StringBuilder();
        Map<String, String> toolMap = new HashMap<>();
        for (Message msg : req.getMessages()) {
            if(msg.getRole().equals("system")) {
                agentInfo.append(msg.getContent()).append("\n");
            }
            if(msg.getRole().equals("assistant")) {
                if(CollectionUtils.isNotEmpty(msg.getTool_calls())) {
                    for(Message.ToolCall call : msg.getTool_calls()) {
                        toolMap.put(call.getId(), call.getFunction().getName());
                    }
                }
            }
        }

        List<String> functions = new ArrayList<>();
        if(req.getFunctions() != null) {
            req.getFunctions().forEach(f -> {
                functions.add(toPython(f));
            });
        }
        if(req.getTools() != null) {
            req.getTools().forEach(t -> {
                if(t.getType().equals("function")) {
                    functions.add(toPython(t.getFunction()));
                }
            });
        }

        Map<String, Object> env = new HashMap<>();
        env.put("req", req);
        env.put("functions", functions);
        env.put("agent_info", agentInfo.toString());
        env.put("toolMap", toolMap);
        String prompt = Renders.render("com/ke/bella/openapi/simulation/function_call_template.pebble", env);
        Message msg = Message.builder()
                .role("user")
                .content(prompt)
                .build();
        return CompletionRequest.builder()
                .user(req.getUser())
                .temperature(req.getTemperature())
                .top_p(req.getTop_p())
                .seed(req.getSeed())
                .frequency_penalty(req.getFrequency_penalty())
                .presence_penalty(req.getPresence_penalty())
                .stream(req.isStream())
                .stream_options(req.getStream_options())
                .logit_bias(req.getLogit_bias())
                .messages(Arrays.asList(msg))
                .build();
    }

    public static String toPython(Function func) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            // Basic information extraction
            String methodName = func.getName();
            String description = func.getDescription() != null ? func.getDescription() : "";

            // Parameter processing
            Function.FunctionParameter functionParam = func.getParameters() == null ? new Function.FunctionParameter() : func.getParameters();
            List<String> requiredParams = functionParam.getRequired() != null ? functionParam.getRequired() : new ArrayList<>();

            // Convert properties Object to JsonNode for easier processing
            JsonNode params = mapper.valueToTree(functionParam.getProperties());

            // Parameter classification processing
            List<Parameter> processedParams = new ArrayList<>();

            Iterator<Map.Entry<String, JsonNode>> fields = params.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String paramName = entry.getKey();
                JsonNode paramDef = entry.getValue();

                // Type mapping
                String pyType = mapTypeToPython(paramDef.get("type").asText(null));
                boolean isRequired = requiredParams.contains(paramName);
                boolean hasEnum = paramDef.has("enum");

                // Handle enum types
                if(hasEnum) {
                    List<String> enumValues = new ArrayList<>();
                    paramDef.get("enum").forEach(enumValue -> enumValues.add("'" + enumValue.asText() + "'"));
                    pyType = "Literal[" + String.join(", ", enumValues) + "]";
                }

                // Build parameter declaration
                String paramDecl = buildParamDeclaration(
                        paramName,
                        pyType,
                        isRequired,
                        hasEnum ? paramDef.get("enum") : null);

                processedParams.add(new Parameter(
                        paramName,
                        paramDecl,
                        paramDef.has("description") ? paramDef.get("description").asText() : ""));
            }

            // Build code template
            StringBuilder result = new StringBuilder();
            result.append("def ").append(methodName).append("(")
                    .append(processedParams.stream()
                            .map(p -> p.declaration)
                            .collect(Collectors.joining(", ")))
                    .append("):\n");

            result.append("    \"\"\"\n")
                    .append("    ").append(description.replace("\n", " ")).append("\n\n")
                    .append("    Args:\n");

            for (Parameter param : processedParams) {
                String typeInfo = param.declaration.split(":")[1].trim();
                result.append("        ")
                        .append(param.name)
                        .append(" (")
                        .append(typeInfo)
                        .append("): ")
                        .append(param.description)
                        .append("\n");
            }

            result.append("    \"\"\"\n")
                    .append("    pass");

            return result.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Python method", e);
        }

    }

    private static String mapTypeToPython(String type) {
        if(type == null)
            return "Any";

        switch (type.toLowerCase()) {
        case "string":
            return "str";
        case "integer":
            return "int";
        case "number":
            return "float";
        case "boolean":
            return "bool";
        case "object":
            return "dict";
        case "array":
            return "list";
        default:
            return "Any";
        }
    }

    private static String buildParamDeclaration(String name, String type, boolean required, JsonNode enums) {
        if(required) {
            return name + ": " + type;
        }

        // Handle optional parameters with enums
        if(enums != null && enums.size() > 0) {
            return name + ": " + type + " = '" + enums.get(0).asText() + "'";
        }

        // Regular optional parameters
        return name + ": Optional[" + type + "] = None";
    }

    private static class Parameter {
        String name;
        String declaration;
        String description;

        Parameter(String name, String declaration, String description) {
            this.name = name;
            this.declaration = declaration;
            this.description = description;
        }
    }

    public static void main(String[] args) throws IOException {
        String code = "```python\ndirectly_response(type=\"公司会议室\", content=\"业务\\\u1233会议\"\n)\n```";
        Choice c = parse("", code);
        if(c.getMessage().getTool_calls() != null) {
            System.out.println(c.getMessage().getTool_calls().get(0).getFunction().getArguments());
        } else {
            System.out.println(c.getMessage().getContent());
        }
    }
}
