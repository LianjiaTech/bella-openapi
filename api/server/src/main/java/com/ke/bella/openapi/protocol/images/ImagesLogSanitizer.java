package com.ke.bella.openapi.protocol.images;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class ImagesLogSanitizer {
    private static final Set<String> IMAGE_ENDPOINTS = new HashSet<>(Arrays.asList(
            "/v1/images/generations",
            "/v1/images/edits",
            "/v1/images/variations"));
    private static final Set<String> REQUEST_IMAGE_FIELDS = new HashSet<>(Arrays.asList(
            "image",
            "image[]",
            "image_b64_json",
            "image_b64_json[]",
            "mask"));
    private static final Set<String> RESPONSE_IMAGE_FIELDS = new HashSet<>(Arrays.asList(
            "b64_json",
            "url"));

    private ImagesLogSanitizer() {
    }

    public static boolean isImagesLog(EndpointProcessData data) {
        return data != null && IMAGE_ENDPOINTS.contains(data.getEndpoint());
    }

    public static EndpointProcessData sanitizedCopy(EndpointProcessData source) {
        if(!isImagesLog(source)) {
            return null;
        }
        EndpointProcessData copy = new EndpointProcessData();
        BeanUtils.copyProperties(source, copy);
        sanitize(copy);
        return copy;
    }

    public static void sanitize(EndpointProcessData data) {
        if(!isImagesLog(data)) {
            return;
        }
        sanitizeRequest(data);
        sanitizeResponse(data);
    }

    private static void sanitizeRequest(EndpointProcessData data) {
        Object request = data.getRequest();
        if(request instanceof ImagesEditRequest) {
            data.setRequest(sanitizeImagesEditRequest((ImagesEditRequest) request));
        } else if(request != null) {
            data.setRequest(sanitizeRequestObject(request));
        }
        if(StringUtils.isNotBlank(data.getRequestSummary())) {
            data.setRequestSummary(sanitizeRequestJson(data.getRequestSummary()));
        }
        if(StringUtils.isNotBlank(data.getRequestRaw())) {
            data.setRequestRaw(sanitizeRequestJson(data.getRequestRaw()));
        }
    }

    private static Object sanitizeImagesEditRequest(ImagesEditRequest request) {
        ObjectNode node = objectNode(request);
        if(node == null) {
            return request.summary();
        }
        sanitizeRequestNode(node);

        MultipartFile[] images = request.getImage();
        if(images != null && images.length > 0) {
            node.set("image", TextNode.valueOf(placeholder("image payload", multipartBytes(images))));
        }
        MultipartFile mask = request.getMask();
        if(mask != null) {
            node.set("mask", TextNode.valueOf(placeholder("image payload", mask.getSize())));
        }
        return node;
    }

    private static Object sanitizeRequestObject(Object request) {
        ObjectNode node = objectNode(request);
        if(node == null) {
            return request;
        }
        sanitizeRequestNode(node);
        return node;
    }

    private static String sanitizeRequestJson(String raw) {
        JsonNode node = JacksonUtils.deserialize(raw);
        if(node == null) {
            return raw;
        }
        sanitizeRequestNode(node);
        return JacksonUtils.serialize(node);
    }

    private static void sanitizeResponse(EndpointProcessData data) {
        OpenapiResponse response = data.getResponse();
        if(response instanceof ImagesResponse) {
            sanitizeImagesResponse((ImagesResponse) response);
        } else if(StringUtils.isNotBlank(data.getResponseRaw())) {
            ImagesResponse rawResponse = JacksonUtils.deserialize(data.getResponseRaw(), ImagesResponse.class);
            if(rawResponse != null) {
                sanitizeImagesResponse(rawResponse);
                data.setResponse(rawResponse);
            }
        }
        if(StringUtils.isNotBlank(data.getResponseRaw())) {
            data.setResponseRaw(sanitizeResponseJson(data.getResponseRaw()));
        }
    }

    private static String sanitizeResponseJson(String raw) {
        JsonNode node = JacksonUtils.deserialize(raw);
        if(node == null) {
            return raw;
        }
        sanitizeResponseNode(node);
        return JacksonUtils.serialize(node);
    }

    private static void sanitizeImagesResponse(ImagesResponse response) {
        if(response == null || response.getData() == null) {
            return;
        }
        for (ImagesResponse.ImageData imageData : response.getData()) {
            if(imageData.getB64_json() != null) {
                imageData.setB64_json(placeholder("image payload", byteLength(imageData.getB64_json())));
            }
            if(imageData.getUrl() != null) {
                imageData.setUrl(placeholder("image url", byteLength(imageData.getUrl())));
            }
        }
    }

    private static ObjectNode objectNode(Object value) {
        JsonNode node = JacksonUtils.toJsonNode(value);
        return node instanceof ObjectNode ? (ObjectNode) node : null;
    }

    private static void sanitizeRequestNode(JsonNode node) {
        sanitizeNode(node, REQUEST_IMAGE_FIELDS, "image payload");
    }

    private static void sanitizeResponseNode(JsonNode node) {
        sanitizeNode(node, RESPONSE_IMAGE_FIELDS, "image payload");
    }

    private static void sanitizeNode(JsonNode node, Set<String> fields, String kind) {
        if(node instanceof ObjectNode) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> iterator = objectNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                JsonNode child = entry.getValue();
                if(fields.contains(entry.getKey())) {
                    String fieldKind = RESPONSE_IMAGE_FIELDS.contains(entry.getKey()) && "url".equals(entry.getKey()) ? "image url" : kind;
                    objectNode.set(entry.getKey(), scrubbedValue(child, fieldKind));
                } else {
                    sanitizeNode(child, fields, kind);
                }
            }
        } else if(node instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode child : arrayNode) {
                sanitizeNode(child, fields, kind);
            }
        }
    }

    private static JsonNode scrubbedValue(JsonNode value, String kind) {
        if(value instanceof ArrayNode) {
            ArrayNode sanitized = JacksonUtils.createArrayNode();
            for (JsonNode child : value) {
                sanitized.add(placeholder(kind, byteLength(child)));
            }
            return sanitized;
        }
        return TextNode.valueOf(placeholder(kind, byteLength(value)));
    }

    private static long multipartBytes(MultipartFile[] files) {
        return Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
    }

    private static long byteLength(JsonNode value) {
        if(value == null || value.isNull()) {
            return 0;
        }
        if(value.isTextual()) {
            return byteLength(value.asText());
        }
        return byteLength(JacksonUtils.serialize(value));
    }

    private static int byteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String placeholder(String kind, long bytes) {
        return "[REMOVED: " + kind + ", " + bytes + " bytes]";
    }
}
