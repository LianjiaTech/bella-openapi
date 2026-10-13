package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.ResponsesApiRequest;
import com.ke.bella.openapi.protocol.message.ClaudeCodeAttributionProcessor;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import okhttp3.Request;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class SessionIdService {
    private static final byte[] LINE_SEPARATOR = "\n".getBytes(StandardCharsets.UTF_8);
    private static final String[] SESSION_ID_HEADERS = {
            "X-Prompt-Cache-Id",
            "session-id",
            "x-claude-code-session-id"
    };

    private SessionIdService() {
    }

    public static void captureFromHeaders(HttpServletRequest request, EndpointProcessData processData) {
        for (String header : SESSION_ID_HEADERS) {
            String sessionId = StringUtils.trimToNull(request.getHeader(header));
            if(sessionId != null) {
                processData.setSessionId(sessionId);
                return;
            }
        }
        processData.setSessionId(null);
    }

    public static String resolveForChat(CompletionRequest request, int hashPrefixBytes, int minPrefixBytes) {
        if(request == null) {
            return null;
        }
        return resolve(request.getPrompt_cache_key(), () -> chatPrefixHash(request, hashPrefixBytes, minPrefixBytes));
    }

    public static String resolveForMessages(MessageRequest request, int hashPrefixBytes, int minPrefixBytes) {
        if(request == null) {
            return null;
        }
        return resolve(null, () -> messagesPrefixHash(request, hashPrefixBytes, minPrefixBytes));
    }

    public static String resolveForResponses(ResponsesApiRequest request, int hashPrefixBytes, int minPrefixBytes) {
        if(request == null) {
            return null;
        }
        return resolve(request.getPrompt_cache_key(), () -> responsesPrefixHash(request, hashPrefixBytes, minPrefixBytes));
    }

    private static String resolve(String promptCacheKey, PrefixHashSupplier prefixHashSupplier) {
        EndpointProcessData processData = EndpointContext.getProcessData();
        String sessionId = StringUtils.trimToNull(processData.getSessionId());
        if(sessionId == null) {
            sessionId = StringUtils.trimToNull(promptCacheKey);
        }
        if(sessionId == null) {
            sessionId = prefixHashSupplier.get();
        }
        processData.setSessionId(sessionId);
        return sessionId;
    }

    public static void inject(Request.Builder builder, CompletionProperty property) {
        String headerName = property == null ? null : StringUtils.trimToNull(property.getSessionId());
        String sessionId = StringUtils.trimToNull(EndpointContext.getProcessData().getSessionId());
        if(headerName == null || sessionId == null) {
            return;
        }
        builder.header(headerName, sessionId);
    }

    private static String chatPrefixHash(CompletionRequest request, int hashPrefixBytes, int minPrefixBytes) {
        PrefixBuffer prefix = new PrefixBuffer(normalizedHashPrefixBytes(hashPrefixBytes));
        appendItemsExcludingLastUser(prefix, request.getMessages(), Message::getRole, (buffer, message) -> {
            buffer.append(message.getRole());
            appendText(buffer, message.getContent());
        });
        return validPrefixHash(prefix, hashPrefixBytes, minPrefixBytes);
    }

    private static String messagesPrefixHash(MessageRequest request, int hashPrefixBytes, int minPrefixBytes) {
        PrefixBuffer prefix = new PrefixBuffer(normalizedHashPrefixBytes(hashPrefixBytes));
        appendText(prefix, ClaudeCodeAttributionProcessor.systemWithoutAttribution(request.getSystem()));
        appendItemsExcludingLastUser(prefix, request.getMessages(), MessageRequest.InputMessage::getRole, (buffer, message) -> {
            appendText(buffer, message.getContent());
            buffer.append(message.getRole());
        });
        return validPrefixHash(prefix, hashPrefixBytes, minPrefixBytes);
    }

    private static String responsesPrefixHash(ResponsesApiRequest request, int hashPrefixBytes, int minPrefixBytes) {
        PrefixBuffer prefix = new PrefixBuffer(normalizedHashPrefixBytes(hashPrefixBytes));
        appendText(prefix, request.getInstructions());
        Object input = request.getInput();
        if(input instanceof String) {
            prefix.append((String) input);
        } else if(input instanceof List) {
            appendItemsExcludingLastUser(prefix, (List<?>) input, SessionIdService::responseRole, SessionIdService::appendResponseItem);
        }
        return validPrefixHash(prefix, hashPrefixBytes, minPrefixBytes);
    }

    private static String validPrefixHash(PrefixBuffer prefix, int hashPrefixBytes, int minPrefixBytes) {
        int minimumBytes = Math.min(Math.max(1, minPrefixBytes), normalizedHashPrefixBytes(hashPrefixBytes));
        return prefix.hasAtLeast(minimumBytes) ? sha256Hex(prefix.bytes()) : null;
    }

    private static int normalizedHashPrefixBytes(int hashPrefixBytes) {
        return Math.max(1, hashPrefixBytes);
    }

    private static <T> void appendItemsExcludingLastUser(PrefixBuffer prefix, List<T> items, Function<T, String> roleReader,
            BiConsumer<PrefixBuffer, T> itemAppender) {
        if(CollectionUtils.isEmpty(items)) {
            return;
        }
        int lastUserIndex = lastUserIndex(items, roleReader);
        for (int index = 0; index < items.size() && !prefix.isFull(); index++) {
            if(index == lastUserIndex) {
                continue;
            }
            T item = items.get(index);
            if(item != null) {
                itemAppender.accept(prefix, item);
            }
        }
    }

    private static <T> int lastUserIndex(List<T> items, Function<T, String> roleReader) {
        for (int index = items.size() - 1; index >= 0; index--) {
            T item = items.get(index);
            if(item != null && "user".equals(roleReader.apply(item))) {
                return index;
            }
        }
        return -1;
    }

    private static String responseRole(Object item) {
        if(item instanceof ResponsesApiRequest.InputItem) {
            return ((ResponsesApiRequest.InputItem) item).getRole();
        }
        if(item instanceof Map) {
            return asString(((Map<?, ?>) item).get("role"));
        }
        return null;
    }

    private static void appendResponseItem(PrefixBuffer prefix, Object item) {
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

    private static void appendText(PrefixBuffer prefix, Object value) {
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

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static String sha256Hex(byte[] value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private interface PrefixHashSupplier {
        String get();
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

        private boolean hasAtLeast(int minimumBytes) {
            return length >= minimumBytes;
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
