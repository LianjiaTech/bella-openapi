package com.ke.bella.openapi.protocol.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true) // toBuilder=true allows easy modification for factory methods
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamMessageResponse {

    private String type;
    private MessageResponse message;
    private Integer index;
    @JsonProperty("content_block")
    private MessageResponse.ContentBlock contentBlock;
    private Object delta; // Holds specific delta objects or MessageDeltaInfo
    private StreamErrorInfo error;
    private StreamUsage usage;


    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type" // This 'type' is specific to the delta object itself
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TextDelta.class, name = "text_delta"),
            @JsonSubTypes.Type(value = InputJsonDelta.class, name = "input_json_delta"),
            @JsonSubTypes.Type(value = ThinkingDelta.class, name = "thinking_delta"),
            @JsonSubTypes.Type(value = SignatureDelta.class, name = "signature_delta"),
            @JsonSubTypes.Type(value = RedactedThinkingDelta.class, name = "redacted_thinking")
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static abstract class Delta {
        private String type;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TextDelta extends Delta {
        private String text;

        public TextDelta(String text) {
            this.setType("text_delta");
            this.text = text;
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputJsonDelta extends Delta {
        @JsonProperty("partial_json")
        private String partialJson;

        public InputJsonDelta(String partialJson) {
            this.setType("input_json_delta");
            this.partialJson = partialJson;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ThinkingDelta extends Delta {
        private String thinking;
        public ThinkingDelta(String thinking) {
            this.setType("thinking_delta");
            this.thinking = thinking;
        }
        public ThinkingDelta() {
            this.setType("thinking_delta");
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SignatureDelta extends Delta {
        private String signature;

        public SignatureDelta(String signature) {
            this.setType("signature_delta");
            this.signature = signature;
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RedactedThinkingDelta extends Delta {
        private String data;

        public RedactedThinkingDelta(String data) {
            this.setType("redacted_thinking");
            this.data = data;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MessageDeltaInfo {
        @JsonProperty("stop_reason")
        private String stopReason;
        @JsonProperty("stop_sequence")
        private String stopSequence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreamUsage {
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outputTokens;
        @JsonProperty("cache_creation_input_tokens")
        private int cacheCreationInputTokens;
        @JsonProperty("cache_read_input_tokens")
        private int cacheReadInputTokens;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreamErrorInfo {
        private String type;
        private String message;
    }

    public static StreamMessageResponse messageStart(MessageResponse initialMessage) {
        return StreamMessageResponse.builder()
                .type("message_start")
                .message(initialMessage)
                .build();
    }

    public static StreamMessageResponse contentBlockStart(int index, MessageResponse.ContentBlock block) {
        return StreamMessageResponse.builder()
                .type("content_block_start")
                .index(index)
                .contentBlock(block)
                .build();
    }

    public static StreamMessageResponse contentBlockDelta(int index, Delta specificBlockDelta) {
        return StreamMessageResponse.builder()
                .type("content_block_delta")
                .index(index)
                .delta(specificBlockDelta)
                .build();
    }

    public static StreamMessageResponse contentBlockStop(int index) {
        return StreamMessageResponse.builder()
                .type("content_block_stop")
                .index(index)
                .build();
    }

    public static StreamMessageResponse messageDelta(MessageDeltaInfo messageDeltaInfo, StreamUsage usage) {
        return StreamMessageResponse.builder()
                .type("message_delta")
                .usage(usage)
                .delta(messageDeltaInfo)
                .build();
    }

    // This is a simpler message_stop if no final message state is needed, just the type
    public static StreamMessageResponse messageStop() {
        return StreamMessageResponse.builder()
                .type("message_stop")
                .build();
    }


    public static StreamMessageResponse ping() {
        return StreamMessageResponse.builder()
                .type("ping")
                .build();
    }

    public static StreamMessageResponse error(String type, String message) {
        return StreamMessageResponse.builder()
                .type("error")
                .error(StreamErrorInfo.builder().type(type).message(message).build())
                .build();
    }


    public static MessageResponse initial(StreamCompletionResponse streamCompletionResponse) {
        String messageId = streamCompletionResponse.getId();
        if (messageId == null || messageId.isEmpty()) {
            messageId = UUID.randomUUID().toString();
        }
        MessageResponse.Usage initialUsage = MessageResponse.Usage.builder()
                .inputTokens(1) // As per plan, initial input tokens set to 1.
                .outputTokens(1) // As per plan, initial output tokens set to 1.
                .build();
        return MessageResponse.builder()
                .id(messageId)
                .type("message")
                .role("assistant")
                .content(Collections.emptyList())
                .model(streamCompletionResponse.getModel() == null ? "LLM" : streamCompletionResponse.getModel())
                .stopReason(null)
                .stopSequence(null)
                .usage(initialUsage)
                .build();
    }
}
