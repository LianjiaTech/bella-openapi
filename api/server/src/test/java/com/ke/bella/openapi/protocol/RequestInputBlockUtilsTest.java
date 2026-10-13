package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestInputBlockUtilsTest {

    @Test
    public void completionRequest_replacesConfiguredBlocks() {
        CompletionRequest request = completionRequestWithMultimodalContent();
        CompletionProperty property = new CompletionProperty();
        property.setInputReplacementTexts(Collections.singletonMap("image", "custom image replacement"));

        RequestInputBlockUtils.replaceInputBlocksWithText(request, property);

        assertThat(request.getMessages().get(0).getContent()).isEqualTo(listOf(
                mapOf("type", "text", "text", "hello"),
                mapOf("type", "text", "text", "custom image replacement")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void completionRequest_replacesConfiguredBlocksIncludingNestedContent() {
        CompletionRequest request = completionRequestWithNestedToolResultContent();

        RequestInputBlockUtils.replaceInputBlocksWithText(request, replacementProperty());

        Map<String, Object> toolResult = (Map<String, Object>) ((List<?>) request.getMessages().get(0).getContent()).get(0);
        List<Map<String, Object>> nestedContent = (List<Map<String, Object>>) toolResult.get("content");
        assertThat(nestedContent).containsExactly(mapOf("type", "text", "text", "custom image replacement"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void messageRequest_replacesTypedBlocksIncludingNestedToolResultContent() {
        MessageRequest request = nestedToolResultMessageRequest();

        RequestInputBlockUtils.replaceInputBlocksWithText(request, replacementProperty());

        List<MessageRequest.ContentBlock> content = (List<MessageRequest.ContentBlock>) request.getMessages().get(0).getContent();
        MessageRequest.ToolResultContentBlock toolResult = (MessageRequest.ToolResultContentBlock) content.get(0);
        List<MessageRequest.ContentBlock> nestedContent = (List<MessageRequest.ContentBlock>) toolResult.getContent();
        assertThat(nestedContent).hasSize(1);
        assertThat(nestedContent.get(0)).isInstanceOf(MessageRequest.TextContentBlock.class);
        assertThat(((MessageRequest.TextContentBlock) nestedContent.get(0)).getText()).isEqualTo("custom image replacement");
    }

    @Test
    public void messageRequest_keepsContentUntouchedWithoutReplacementTexts() {
        MessageRequest request = nestedToolResultMessageRequest();
        Object originalContent = request.getMessages().get(0).getContent();
        CompletionProperty property = new CompletionProperty();
        property.setInputReplacementEnabled(true);

        RequestInputBlockUtils.replaceInputBlocksWithText(request, property);

        assertThat(request.getMessages().get(0).getContent()).isSameAs(originalContent);
    }

    private CompletionRequest completionRequestWithMultimodalContent() {
        return CompletionRequest.builder()
                .messages(Collections.singletonList(Message.builder()
                        .role("user")
                        .content(multimodalContent())
                        .build()))
                .build();
    }

    private CompletionRequest completionRequestWithNestedToolResultContent() {
        return CompletionRequest.builder()
                .messages(Collections.singletonList(Message.builder()
                        .role("user")
                        .content(Collections.singletonList(mapOf("type", "tool_result", "content",
                                Collections.singletonList(mapOf("type", "image", "source", mapOf("type", "base64"))))))
                        .build()))
                .build();
    }

    private MessageRequest nestedToolResultMessageRequest() {
        MessageRequest.ImageContentBlock image = new MessageRequest.ImageContentBlock();
        MessageRequest.Base64ImageSource source = new MessageRequest.Base64ImageSource();
        source.setMediaType("image/png");
        source.setData("abc");
        image.setSource(source);

        MessageRequest.ToolResultContentBlock toolResult = new MessageRequest.ToolResultContentBlock();
        toolResult.setToolUseId("tool-use-1");
        toolResult.setContent(Collections.singletonList(image));

        return MessageRequest.builder()
                .messages(Collections.singletonList(MessageRequest.InputMessage.builder()
                        .role("user")
                        .content(Collections.singletonList(toolResult))
                        .build()))
                .build();
    }

    private CompletionProperty replacementProperty() {
        CompletionProperty property = new CompletionProperty();
        property.setInputReplacementEnabled(true);
        property.setInputReplacementTexts(Collections.singletonMap("image", "custom image replacement"));
        return property;
    }

    private List<Map<String, Object>> multimodalContent() {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(mapOf("type", "text", "text", "hello"));
        content.add(mapOf("type", "image", "source", mapOf("type", "base64")));
        return content;
    }

    @SafeVarargs
    private final List<Map<String, Object>> listOf(Map<String, Object>... items) {
        List<Map<String, Object>> list = new ArrayList<>();
        Collections.addAll(list, items);
        return list;
    }

    private Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private Map<String, Object> mapOf(String key, Object value, String secondKey, Object secondValue) {
        Map<String, Object> map = mapOf(key, value);
        map.put(secondKey, secondValue);
        return map;
    }
}
