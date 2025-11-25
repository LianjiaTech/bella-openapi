package com.ke.bella.openapi.protocol.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageRequest implements IMemoryClearable {
    private String anthropic_version;
    private String model;
    private List<InputMessage> messages;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    private Metadata metadata;
    @JsonProperty("stop_sequences")
    private List<String> stopSequences;
    private Boolean stream;
    @JsonDeserialize(using = SystemFieldDeserializer.class)
    private Object system; // String or List<RequestTextBlock>
    private Float temperature;
    private ThinkingConfig thinking;
    @JsonProperty("tool_choice")
    private ToolChoice toolChoice;
    private List<Tool> tools;
    @JsonProperty("top_k")
    private Integer topK;
    @JsonProperty("top_p")
    private Float topP;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputMessage {
        private String role; // "user" or "assistant"
        @JsonDeserialize(using = ContentFieldDeserializer.class)
        private Object content; // String or List<ContentBlock>
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TextContentBlock.class, name = "text"),
            @JsonSubTypes.Type(value = ImageContentBlock.class, name = "image"),
            @JsonSubTypes.Type(value = DocumentContentBlock.class, name = "document"),
            @JsonSubTypes.Type(value = ToolUseContentBlock.class, name = "tool_use"),
            @JsonSubTypes.Type(value = ToolResultContentBlock.class, name = "tool_result"),
            @JsonSubTypes.Type(value = ThinkingContentBlock.class, name = "thinking"),
            @JsonSubTypes.Type(value = RedactedThinkingContentBlock.class, name = "redacted_thinking")
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static abstract class ContentBlock {
        private String type;
        private Object cache_control;
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TextContentBlock extends ContentBlock {
        private String text;
        public String getType() {
            return "text";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ThinkingContentBlock extends ContentBlock {
        private String thinking;
        private String signature;
        public String getType() {
            return "thinking";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RedactedThinkingContentBlock extends ContentBlock {
        private String data;
        public String getType() {
            return "redacted_thinking";
        }
    }


    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageContentBlock extends ContentBlock {
        private ImageSource source;
        public String getType() {
            return "image";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentContentBlock extends ContentBlock {
        private DocumentSource source;
        public String getType() {
            return "document";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolUseContentBlock extends ContentBlock {
        private String id;
        private String name;
        private Object input; // Consider defining a more specific type if schema is known

        public String getType() {
            return "tool_use";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolResultContentBlock extends ContentBlock {
        @JsonProperty("tool_use_id")
        private String toolUseId;
        private Object content; // String or List<ContentBlock> for complex results
        @JsonProperty("is_error")
        private Boolean isError;

        public String getType() {
            return "tool_result";
        }
    }


    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Base64ImageSource.class, name = "base64"),
        @JsonSubTypes.Type(value = URLImageSource.class, name = "url")
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static abstract class ImageSource {
        private String type;
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Base64ImageSource extends ImageSource {
        @JsonProperty("media_type")
        private String mediaType;
        private String data;
        public String getType() {
            return "base64";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class URLImageSource extends ImageSource {
        private String url;
        public String getType() {
            return "url";
        }
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = UrlDocumentSource.class, name = "url"),
        @JsonSubTypes.Type(value = Base64DocumentSource.class, name = "base64"),
        @JsonSubTypes.Type(value = FileDocumentSource.class, name = "file"),
        @JsonSubTypes.Type(value = ContentDocumentSource.class, name = "content"),
        @JsonSubTypes.Type(value = TextDocumentSource.class, name = "text")
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static abstract class DocumentSource {
        private String type;
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UrlDocumentSource extends DocumentSource {
        private String url;
        public String getType() {
            return "url";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Base64DocumentSource extends DocumentSource {
        @JsonProperty("media_type")
        private String mediaType;
        private String data;
        public String getType() {
            return "base64";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FileDocumentSource extends DocumentSource {
        @JsonProperty("file_id")
        private String fileId;
        public String getType() {
            return "file";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentDocumentSource extends DocumentSource {
        private String content;
        public String getType() {
            return "content";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TextDocumentSource extends DocumentSource {
        private String data;
        @JsonProperty("media_type")
        private String mediaType; // "text/plain"
        public String getType() {
            return "text";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Metadata {
        @JsonProperty("user_id")
        private String userId;
    }

    // For the 'system' field if it can be List<RequestTextBlock>
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RequestTextBlock {
        private String type; // "text"
        private String text;
        private Object cache_control;
    }


    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        defaultImpl = ThinkingConfigEnabled.class) // Default might be tricky; adjust as needed
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ThinkingConfigEnabled.class, name = "enabled"),
        @JsonSubTypes.Type(value = ThinkingConfigDisabled.class, name = "disabled")
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static abstract class ThinkingConfig {
         private String type;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    public static class ThinkingConfigEnabled extends ThinkingConfig {
        private Integer budget_tokens;
        public ThinkingConfigEnabled(Integer budget_tokens) {
            super("enabled");
            this.budget_tokens = budget_tokens;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ThinkingConfigDisabled extends ThinkingConfig {
        public ThinkingConfigDisabled() {
            super("disabled");
        }
    }


    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type") // 'type' is the discriminator for ToolChoice
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ToolChoiceAuto.class, name = "auto"),
            @JsonSubTypes.Type(value = ToolChoiceAny.class, name = "any"),
            @JsonSubTypes.Type(value = ToolChoiceTool.class, name = "tool"),
            @JsonSubTypes.Type(value = ToolChoiceNone.class, name = "none")
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Data
    public static abstract class ToolChoice {
        private String type;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolChoiceAuto extends ToolChoice {
        public String getType() {
            return "auto";
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolChoiceAny extends ToolChoice {
        public String getType() {
            return "any";
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolChoiceNone extends ToolChoice {
        public String getType() {
            return "none";
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolChoiceTool extends ToolChoice {
        private String name;
        public String getType() {
            return "tool";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tool {
        private String name;
        private String description;
        @JsonProperty("input_schema")
        private InputSchema inputSchema;
        private Object cache_control;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputSchema {
        private String type; // e.g., "object"
        private Object properties;
        private List<String> required;
        private boolean additionalProperties;
    }


    public static class SystemFieldDeserializer extends StdDeserializer<Object> {
        public SystemFieldDeserializer() {
            this(null);
        }
        public SystemFieldDeserializer(Class<?> vc) {
            super(vc);
        }
        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            if(node.isTextual()) {
                return node.asText();
            } else if(node.isArray()) {
                ObjectMapper mapper = (ObjectMapper) p.getCodec();
                return mapper.convertValue(node, new TypeReference<List<RequestTextBlock>>() {});

            } else {
                throw new JsonMappingException(p, "System field must be either String or List<RequestTextBlock>");
            }
        }
    }

    public static class ContentFieldDeserializer extends StdDeserializer<Object> {
        public ContentFieldDeserializer() {
            this(null);
        }
        public ContentFieldDeserializer(Class<?> vc) {
            super(vc);
        }
        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            if(node.isTextual()) {
                return node.asText();
            } else if(node.isArray()) {
                ObjectMapper mapper = (ObjectMapper) p.getCodec();
                return mapper.convertValue(node, new TypeReference<List<ContentBlock>>() {});

            } else {
                throw new JsonMappingException(p, "Content field must be either String or List<ContentBlock>");
            }
        }
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if (!cleared) {
            // 清理占用大量内存的字段
            this.messages = null;
            this.system = null;
            this.tools = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
