package com.ke.bella.openapi.protocol.alpha.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;

public final class AlphaSearchRequest {
    private final byte[] body;
    private final ObjectNode json;

    private AlphaSearchRequest(byte[] body, ObjectNode json) {
        this.body = Arrays.copyOf(body, body.length);
        this.json = json;
    }

    public static AlphaSearchRequest parse(byte[] body) {
        if(body == null) {
            throw new IllegalArgumentException("request body must be valid JSON");
        }
        try {
            JsonNode request = JacksonUtils.MAPPER.readTree(body);
            if(request == null || !request.isObject()) {
                throw new IllegalArgumentException("request body must be a JSON object");
            }
            ObjectNode object = (ObjectNode) request;
            JsonNode model = object.get("model");
            if(model == null || !model.isTextual() || StringUtils.isBlank(model.asText())) {
                throw new IllegalArgumentException("model is required");
            }
            return new AlphaSearchRequest(body, object);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("request body must be valid JSON", e);
        }
    }

    public String getModel() {
        return json.get("model").asText();
    }

    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    public JsonNode asJson() {
        return json.deepCopy();
    }

    public byte[] bodyWithModel(String model) {
        if(StringUtils.isBlank(model) || model.equals(getModel())) {
            return body();
        }
        ObjectNode mapped = json.deepCopy();
        mapped.put("model", model);
        return JacksonUtils.toByte(mapped);
    }
}
