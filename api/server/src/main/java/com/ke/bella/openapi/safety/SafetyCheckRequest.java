package com.ke.bella.openapi.safety;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiRequest;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@SuperBuilder
public class SafetyCheckRequest {
    private String requestId;
    // input output
    private String type;
    private String akCode;
    private String sceneCode;
    private String serviceId;
    private String userId;
    private String model;
    private String supplier;
    private boolean isRequest;
    private String clientIp;

    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    @Data
    public static class Chat extends SafetyCheckRequest {
        private List<Message> messages;
        /**
         * API endpoint 路径，如 /v1/chat/completions
         */
        private String endpoint;
        /**
         * 完整的原始请求体，用于 llm_payload 传递
         */
        private Object originalRequest;

        @Data
        public static class Message {
            String role;
            String content;
        }

        public static Chat convertFrom(CompletionRequest completionRequest, EndpointProcessData processData, ApikeyInfo apikeyInfo) {
            return Chat.builder()
                    .requestId(processData.getRequestId())
                    .type("input")
                    .akCode(apikeyInfo.getCode())
                    .sceneCode(apikeyInfo.getSafetySceneCode())
                    .serviceId(apikeyInfo.getServiceId())
                    .userId(processData.getUser())
                    .model(processData.getModel())
                    .supplier(processData.getSupplier())
                    .endpoint(processData.getEndpoint())
                    .clientIp(processData.getClientIp())
                    .originalRequest(completionRequest)
                    .isRequest(true)
                    .build();
        }

        public static Chat convertFrom(CompletionResponse completionResponse, EndpointProcessData processData, ApikeyInfo apikeyInfo) {
            if(CollectionUtils.isEmpty(completionResponse.getChoices())) {
                return null;
            }
            List<com.ke.bella.openapi.protocol.completion.Message> messages = completionResponse.getChoices().stream()
                    .map(CompletionResponse.Choice::getMessage)
                    .collect(Collectors.toList());
            return Chat.builder()
                    .requestId(processData.getRequestId())
                    .type("output")
                    .akCode(apikeyInfo.getCode())
                    .sceneCode(apikeyInfo.getSafetySceneCode())
                    .serviceId(apikeyInfo.getServiceId())
                    .userId(processData.getUser())
                    .model(processData.getModel())
                    .supplier(processData.getSupplier())
                    .endpoint(processData.getEndpoint())
                    .clientIp(processData.getClientIp())
                    .messages(SafetyCheckRequest.Chat.convertFrom(messages))
                    .originalRequest(completionResponse)
                    .isRequest(false)
                    .build();
        }

        public static Chat convertFrom(MessageRequest messageRequest, EndpointProcessData processData, ApikeyInfo apikeyInfo) {
            return Chat.builder()
                    .requestId(processData.getRequestId())
                    .type("input")
                    .akCode(apikeyInfo.getCode())
                    .sceneCode(apikeyInfo.getSafetySceneCode())
                    .serviceId(apikeyInfo.getServiceId())
                    .userId(processData.getUser())
                    .model(processData.getModel())
                    .supplier(processData.getSupplier())
                    .endpoint(processData.getEndpoint())
                    .clientIp(processData.getClientIp())
                    .originalRequest(messageRequest)
                    .isRequest(true)
                    .build();
        }

        public static Chat convertFrom(MessageResponse messageResponse, EndpointProcessData processData, ApikeyInfo apikeyInfo) {
            return Chat.builder()
                    .requestId(processData.getRequestId())
                    .type("output")
                    .akCode(apikeyInfo.getCode())
                    .sceneCode(apikeyInfo.getSafetySceneCode())
                    .serviceId(apikeyInfo.getServiceId())
                    .userId(processData.getUser())
                    .model(processData.getModel())
                    .supplier(processData.getSupplier())
                    .endpoint(processData.getEndpoint())
                    .clientIp(processData.getClientIp())
                    .originalRequest(messageResponse)
                    .isRequest(false)
                    .build();
        }

        public static Chat convertFrom(ResponsesApiRequest request, EndpointProcessData processData, ApikeyInfo apikeyInfo) {
            return Chat.builder()
                    .requestId(processData.getRequestId())
                    .type("input")
                    .akCode(apikeyInfo.getCode())
                    .sceneCode(apikeyInfo.getSafetySceneCode())
                    .serviceId(apikeyInfo.getServiceId())
                    .userId(processData.getUser())
                    .model(processData.getModel())
                    .supplier(processData.getSupplier())
                    .endpoint(processData.getEndpoint())
                    .clientIp(processData.getClientIp())
                    .originalRequest(request)
                    .isRequest(true)
                    .build();
        }

        public static Chat convertFrom(ResponsesApiResponse response, EndpointProcessData processData, ApikeyInfo apikeyInfo) {
            return Chat.builder()
                    .requestId(processData.getRequestId())
                    .type("output")
                    .akCode(apikeyInfo.getCode())
                    .sceneCode(apikeyInfo.getSafetySceneCode())
                    .serviceId(apikeyInfo.getServiceId())
                    .userId(processData.getUser())
                    .model(processData.getModel())
                    .supplier(processData.getSupplier())
                    .endpoint(processData.getEndpoint())
                    .clientIp(processData.getClientIp())
                    .originalRequest(response)
                    .isRequest(false)
                    .build();
        }

        public static List<Message> convertFrom(List<com.ke.bella.openapi.protocol.completion.Message> messages) {
            List<Message> checks = new ArrayList<>();
            for (com.ke.bella.openapi.protocol.completion.Message message : messages) {
                checks.addAll(convertFrom(message));
            }
            return checks;
        }

        public static List<Message> convertFrom(com.ke.bella.openapi.protocol.completion.Message message) {
            return convertFrom(message.getRole(), message.getContent());
        }

        public static List<Message> convertFrom(String role, Object content) {
            List<Message> checks = new ArrayList<>();
            if(content != null) {
                Message target = new Message();
                target.setRole(role);
                if(content instanceof Collection) {
                    List<String> textMessages = new LinkedList<>();
                    for (Object contentObj : (Collection) content) {
                        Map contentMap;
                        if(contentObj instanceof Map) {
                            contentMap = (Map) contentObj;
                        } else {
                            contentMap = JacksonUtils.toMap(contentObj);
                        }
                        if(contentMap == null) {
                            continue;
                        }
                        if(contentMap.containsKey("text")) {
                            textMessages.add((String) contentMap.get("text"));
                        }
                    }
                    target.setContent(JacksonUtils.serialize(String.join("\r\n", textMessages)));
                } else {
                    target.setContent(content.toString());
                }
                if(StringUtils.isNotBlank(target.getContent())) {
                    checks.add(target);
                }
            }
            return checks;
        }

    }
}
