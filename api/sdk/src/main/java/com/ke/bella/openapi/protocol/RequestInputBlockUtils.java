package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class RequestInputBlockUtils {
    private RequestInputBlockUtils() {
    }

    public static void replaceInputBlocksWithText(MessageRequest request, CompletionProperty property) {
        replaceInputBlocksWithText(property, replacementTexts ->
                request.getMessages().forEach(message ->
                        message.setContent(replaceMessageContent(message.getContent(), replacementTexts))));
    }

    public static void replaceInputBlocksWithText(CompletionRequest request, CompletionProperty property) {
        replaceInputBlocksWithText(property, replacementTexts ->
                request.getMessages().forEach(message ->
                        message.setContent(replaceContent(message.getContent(), replacementTexts))));
    }

    private static void replaceInputBlocksWithText(CompletionProperty property, Consumer<Map<String, String>> replacer) {
        Map<String, String> replacementTexts = effectiveReplacementTexts(property);
        if(replacementTexts.isEmpty()) {
            return;
        }
        replacer.accept(replacementTexts);
    }

    private static Object replaceMessageContent(Object content, Map<String, String> replacementTexts) {
        if(content instanceof MessageRequest.ContentBlock) {
            MessageRequest.ContentBlock block = (MessageRequest.ContentBlock) content;
            String replacementText = replacementTexts.get(block.getType());
            if(replacementText != null) {
                return buildTextContentBlock(replacementText);
            }
            if(block instanceof MessageRequest.ToolResultContentBlock) {
                MessageRequest.ToolResultContentBlock toolResult = (MessageRequest.ToolResultContentBlock) block;
                toolResult.setContent(replaceMessageContent(toolResult.getContent(), replacementTexts));
            }
            return block;
        }
        if(content instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) content) {
                result.add(replaceMessageContent(item, replacementTexts));
            }
            return result;
        }
        if(content instanceof Map) {
            Map<?, ?> item = (Map<?, ?>) content;
            Object type = item.get("type");
            String replacementText = type == null ? null : replacementTexts.get(type.toString());
            if(replacementText != null) {
                return buildTextBlock(replacementText);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            item.forEach((key, value) -> result.put(String.valueOf(key), replaceMessageContent(value, replacementTexts)));
            return result;
        }
        return content;
    }

    private static Object replaceContent(Object content, Map<String, String> replacementTexts) {
        if(content instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) content) {
                result.add(replaceContent(item, replacementTexts));
            }
            return result;
        }
        if(content instanceof Map) {
            Map<?, ?> item = (Map<?, ?>) content;
            Object type = item.get("type");
            String replacementText = type == null ? null : replacementTexts.get(type.toString());
            if(replacementText != null) {
                return buildTextBlock(replacementText);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            item.forEach((key, value) -> result.put(String.valueOf(key), replaceContent(value, replacementTexts)));
            return result;
        }
        return content;
    }

    private static Map<String, String> effectiveReplacementTexts(CompletionProperty property) {
        Map<String, String> replacementTexts = new HashMap<>();
        if(property.getInputReplacementTexts() != null) {
            replacementTexts.putAll(property.getInputReplacementTexts());
        }
        return replacementTexts;
    }

    private static MessageRequest.TextContentBlock buildTextContentBlock(String text) {
        MessageRequest.TextContentBlock block = new MessageRequest.TextContentBlock();
        block.setText(text);
        return block;
    }

    private static Map<String, Object> buildTextBlock(String text) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "text");
        block.put("text", text);
        return block;
    }
}
