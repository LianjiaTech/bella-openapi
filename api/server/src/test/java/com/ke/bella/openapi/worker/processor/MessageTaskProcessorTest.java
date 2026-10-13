package com.ke.bella.openapi.worker.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.message.MessageRequest;

public class MessageTaskProcessorTest {
    private static final String ATTRIBUTION = "x-anthropic-billing-header: cc_version=2.1.153.2cc; cc_entrypoint=sdk-py; cch=2ac08;";

    @Test
    public void prepareOutboundRequest_stripsAttributionWhenWorkerChannelEnablesIt() {
        MessageRequest request = MessageRequest.builder()
                .system(Arrays.asList(textBlock(ATTRIBUTION), textBlock("Stable prompt")))
                .build();
        CompletionProperty property = new CompletionProperty();
        property.setStripClaudeCodeAttribution(true);

        MessageRequest outbound = MessageTaskProcessor.prepareOutboundRequest(request, property);

        assertThat(systemBlocks(outbound)).extracting(MessageRequest.RequestTextBlock::getText)
                .containsExactly("Stable prompt");
        assertThat(systemBlocks(request)).extracting(MessageRequest.RequestTextBlock::getText)
                .containsExactly(ATTRIBUTION, "Stable prompt");
    }

    private MessageRequest.RequestTextBlock textBlock(String text) {
        return MessageRequest.RequestTextBlock.builder().type("text").text(text).build();
    }

    @SuppressWarnings("unchecked")
    private List<MessageRequest.RequestTextBlock> systemBlocks(MessageRequest request) {
        return (List<MessageRequest.RequestTextBlock>) request.getSystem();
    }
}
