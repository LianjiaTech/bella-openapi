package com.ke.bella.openapi.protocol.message;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClaudeCodeAttributionProcessor {
    private static final String HORIZONTAL_SPACE = "[ \\t]{0,8}";
    private static final String FIELD_SPACE = "[ \\t\\r\\n]{0,8}";
    private static final String FIELD_VALUE = "[^;\\r\\n]{1,128}";
    private static final String ATTRIBUTION = "\\Ax-anthropic-billing-header" + HORIZONTAL_SPACE + ":" + FIELD_SPACE
            + "cc_version" + HORIZONTAL_SPACE + "=" + HORIZONTAL_SPACE + FIELD_VALUE + HORIZONTAL_SPACE + ";" + FIELD_SPACE
            + "cc_entrypoint" + HORIZONTAL_SPACE + "=" + HORIZONTAL_SPACE + FIELD_VALUE + HORIZONTAL_SPACE + ";" + FIELD_SPACE
            + "cch" + HORIZONTAL_SPACE + "=" + HORIZONTAL_SPACE + FIELD_VALUE + HORIZONTAL_SPACE + ";?";
    private static final Pattern ATTRIBUTION_BLOCK_PATTERN = Pattern.compile(ATTRIBUTION + HORIZONTAL_SPACE + "(?:\\r\\n|\\n)?\\z");
    private static final Pattern ATTRIBUTION_PREFIX_PATTERN = Pattern.compile(ATTRIBUTION + HORIZONTAL_SPACE + "(?:\\r\\n|\\n)?");

    private ClaudeCodeAttributionProcessor() {
    }

    public static MessageRequest prepareRequest(MessageRequest request, boolean stripAttribution) {
        boolean alreadyStripped = request.getSystem() instanceof AttributionStrippedSystemList;
        MessageRequest copy = request.copyRequest();
        if(stripAttribution) {
            // copyRequest是浅拷贝，只能替换system，禁止原地修改共享的List或block
            copy.setSystem(alreadyStripped ? markAsStripped(copy.getSystem()) : systemWithoutAttribution(copy.getSystem()));
        }
        return copy;
    }

    public static Object systemWithoutAttribution(Object system) {
        if(system instanceof String) {
            return stripString((String) system);
        }
        if(system instanceof List) {
            return stripFirstBlock((List<?>) system);
        }
        return system;
    }

    private static Object stripString(String system) {
        Matcher matcher = ATTRIBUTION_PREFIX_PATTERN.matcher(system);
        return matcher.lookingAt() ? emptyToNull(system.substring(matcher.end())) : system;
    }

    private static Object stripFirstBlock(List<?> system) {
        if(system instanceof AttributionStrippedSystemList || system.isEmpty() || !(system.get(0) instanceof MessageRequest.RequestTextBlock)) {
            return system;
        }
        MessageRequest.RequestTextBlock firstBlock = (MessageRequest.RequestTextBlock) system.get(0);
        if(firstBlock.getText() == null || !ATTRIBUTION_BLOCK_PATTERN.matcher(firstBlock.getText()).matches()) {
            return system;
        }
        if(system.size() == 1) {
            return null;
        }
        return new AttributionStrippedSystemList(system.subList(1, system.size()));
    }

    private static Object markAsStripped(Object system) {
        return system instanceof List ? new AttributionStrippedSystemList((List<?>) system) : system;
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static class AttributionStrippedSystemList extends ArrayList<Object> {
        private static final long serialVersionUID = 1L;

        private AttributionStrippedSystemList(List<?> blocks) {
            super(blocks);
        }
    }
}
