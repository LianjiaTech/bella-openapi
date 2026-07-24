package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.ResponsesApiRequest;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Component
public class RouteAffinityKeyGenerator {
    private static final String KEY_PREFIX = "bella-openapi-route-affinity";
    private static final byte[] LINE_SEPARATOR = "\n".getBytes(StandardCharsets.UTF_8);

    @Value("${bella.openapi.route-affinity.enabled:true}")
    private boolean enabled;

    @Value("${bella.openapi.route-affinity.hash-prefix-bytes:1024}")
    private int hashPrefixBytes;

    @Value("${bella.openapi.route-affinity.min-prefix-bytes:1024}")
    private int minPrefixBytes;

    public String forChat(String endpoint, String model, CompletionRequest request) {
        if(request == null || CollectionUtils.isEmpty(request.getMessages())) {
            return null;
        }
        PrefixBuffer prefix = new PrefixBuffer(hashPrefixBytes());
        appendItemsExcludingLastUser(prefix, request.getMessages(), Message::getRole, (buffer, message) -> {
            buffer.append(message.getRole());
            appendText(buffer, message.getContent());
        });
        return buildKey(endpoint, model, prefix);
    }

    public String forMessages(String endpoint, String model, MessageRequest request) {
        if(request == null) {
            return null;
        }
        PrefixBuffer prefix = new PrefixBuffer(hashPrefixBytes());
        appendText(prefix, request.getSystem());
        appendItemsExcludingLastUser(prefix, request.getMessages(), MessageRequest.InputMessage::getRole, (buffer, message) -> {
            appendText(buffer, message.getContent());
            buffer.append(message.getRole());
        });
        return buildKey(endpoint, model, prefix);
    }

    public String forResponses(String endpoint, String model, ResponsesApiRequest request) {
        if(request == null) {
            return null;
        }
        PrefixBuffer prefix = new PrefixBuffer(hashPrefixBytes());
        appendText(prefix, request.getInstructions());
        Object input = request.getInput();
        if(input instanceof String) {
            prefix.append((String) input);
        } else if(input instanceof List) {
            appendItemsExcludingLastUser(prefix, (List<?>) input, this::responseRole, this::appendResponseItem);
        }
        return buildKey(endpoint, model, prefix);
    }

    private String buildKey(String endpoint, String model, PrefixBuffer prefix) {
        int minBytes = Math.min(Math.max(1, minPrefixBytes), hashPrefixBytes());
        if(!enabled || StringUtils.isBlank(endpoint) || StringUtils.isBlank(model) || !prefix.hasAtLeast(minBytes)) {
            return null;
        }
        return KEY_PREFIX + ":" + endpoint + ":" + model + ":" + sha256Hex(prefix.bytes());
    }

    private int hashPrefixBytes() {
        return Math.max(1, hashPrefixBytes);
    }

    private <T> void appendItemsExcludingLastUser(PrefixBuffer prefix, List<T> items, Function<T, String> roleReader,
            BiConsumer<PrefixBuffer, T> itemAppender) {
        if(CollectionUtils.isEmpty(items)) {
            return;
        }
        int lastUserIndex = lastUserIndex(items, roleReader);
        for (int i = 0; i < items.size() && !prefix.isFull(); i++) {
            if(i == lastUserIndex) {
                continue;
            }
            T item = items.get(i);
            if(item != null) {
                itemAppender.accept(prefix, item);
            }
        }
    }

    private <T> int lastUserIndex(List<T> items, Function<T, String> roleReader) {
        for (int i = items.size() - 1; i >= 0; i--) {
            T item = items.get(i);
            if(item != null && "user".equals(roleReader.apply(item))) {
                return i;
            }
        }
        return -1;
    }

    private String responseRole(Object item) {
        if(item instanceof ResponsesApiRequest.InputItem) {
            return ((ResponsesApiRequest.InputItem) item).getRole();
        }
        if(item instanceof Map) {
            return asString(((Map<?, ?>) item).get("role"));
        }
        return null;
    }

    private void appendResponseItem(PrefixBuffer prefix, Object item) {
        if(item instanceof ResponsesApiRequest.InputItem) {
            ResponsesApiRequest.InputItem inputItem = (ResponsesApiRequest.InputItem) item;
            prefix.append(inputItem.getRole());
            appendText(prefix, inputItem.getContent());
            return;
        }
        if(item instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) item;
            prefix.append(asString(map.get("role")));
            appendText(prefix, map.get("content"));
        }
    }

    private void appendText(PrefixBuffer prefix, Object value) {
        if(value == null || prefix.isFull()) {
            return;
        }
        if(value instanceof String) {
            prefix.append((String) value);
            return;
        }
        if(value instanceof MessageRequest.RequestTextBlock) {
            prefix.append(((MessageRequest.RequestTextBlock) value).getText());
            return;
        }
        if(value instanceof MessageRequest.TextContentBlock) {
            prefix.append(((MessageRequest.TextContentBlock) value).getText());
            return;
        }
        if(value instanceof MessageRequest.ToolResultContentBlock) {
            appendText(prefix, ((MessageRequest.ToolResultContentBlock) value).getContent());
            return;
        }
        if(value instanceof ResponsesApiRequest.ContentItem) {
            prefix.append(((ResponsesApiRequest.ContentItem) value).getText());
            return;
        }
        if(value instanceof List) {
            for (Object item : (List<?>) value) {
                appendText(prefix, item);
                if(prefix.isFull()) {
                    return;
                }
            }
            return;
        }
        if(value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            appendText(prefix, map.get("text"));
            appendText(prefix, map.get("content"));
            appendText(prefix, map.get("output"));
        }
    }

    private String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static class PrefixBuffer {
        private final byte[] bytes;
        private int length = 0;

        private PrefixBuffer(int capacity) {
            this.bytes = new byte[capacity];
        }

        private void append(String text) {
            if(StringUtils.isBlank(text) || isFull()) {
                return;
            }
            if(appendUtf8(text) && !isFull()) {
                appendBytes(LINE_SEPARATOR);
            }
        }

        private boolean isFull() {
            return length >= bytes.length;
        }

        private boolean hasAtLeast(int minPrefixBytes) {
            return length >= minPrefixBytes;
        }

        private byte[] bytes() {
            return Arrays.copyOf(bytes, length);
        }

        private boolean appendUtf8(String text) {
            boolean appended = false;
            for (int offset = 0; offset < text.length() && !isFull(); ) {
                int codePoint = text.codePointAt(offset);
                byte[] fragment = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                if(!appendBytes(fragment)) {
                    return appended;
                }
                appended = true;
                offset += Character.charCount(codePoint);
            }
            return appended;
        }

        private boolean appendBytes(byte[] value) {
            if(length + value.length > bytes.length) {
                return false;
            }
            System.arraycopy(value, 0, bytes, length, value.length);
            length += value.length;
            return true;
        }
    }
}
