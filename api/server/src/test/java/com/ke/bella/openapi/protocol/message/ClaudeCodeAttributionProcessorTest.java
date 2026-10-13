package com.ke.bella.openapi.protocol.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ClaudeCodeAttributionProcessorTest {
    private static final String ATTRIBUTION = "x-anthropic-billing-header: cc_version=2.1.153.2cc; cc_entrypoint=sdk-py; cch=2ac08;";

    @Test
    public void prepareRequest_arraySystemStripsFirstAttributionWithoutMutatingOriginal() {
        MessageRequest.RequestTextBlock attribution = textBlock(ATTRIBUTION);
        MessageRequest.RequestTextBlock prompt = textBlock("You are Claude Code...");
        MessageRequest original = MessageRequest.builder().system(Arrays.asList(attribution, prompt)).build();

        MessageRequest prepared = ClaudeCodeAttributionProcessor.prepareRequest(original, true);

        assertThat(prepared).isNotSameAs(original);
        assertThat(systemBlocks(prepared)).extracting(MessageRequest.RequestTextBlock::getText)
                .containsExactly("You are Claude Code...");
        assertThat(systemBlocks(original)).containsExactly(attribution, prompt);
        assertThat(systemBlocks(prepared)).isNotSameAs(systemBlocks(original));
    }

    @Test
    public void prepareRequest_disabledKeepsAttributionInShallowCopy() {
        MessageRequest.RequestTextBlock attribution = textBlock(ATTRIBUTION);
        MessageRequest original = MessageRequest.builder().system(Collections.singletonList(attribution)).build();

        MessageRequest prepared = ClaudeCodeAttributionProcessor.prepareRequest(original, false);

        assertThat(prepared).isNotSameAs(original);
        assertThat(systemBlocks(prepared)).extracting(MessageRequest.RequestTextBlock::getText)
                .containsExactly(ATTRIBUTION);
        assertThat(systemBlocks(prepared)).isSameAs(systemBlocks(original));
    }

    @Test
    public void systemWithoutAttribution_stringPrefixSupportsCrLfAndPreservesPrompt() {
        String system = "x-anthropic-billing-header: \r\ncc_version = version; \r\ncc_entrypoint=sdk-py;\r\ncch=value;\r\nStable prompt";

        assertThat(ClaudeCodeAttributionProcessor.systemWithoutAttribution(system)).isEqualTo("Stable prompt");
    }

    @Test
    public void systemWithoutAttribution_supportsMissingTrailingSemicolon() {
        String stringSystem = "x-anthropic-billing-header: cc_version=version; cc_entrypoint=sdk-py; cch=value\nStable prompt";
        List<MessageRequest.RequestTextBlock> blockSystem = Arrays.asList(
                textBlock("x-anthropic-billing-header: cc_version=version; cc_entrypoint=sdk-py; cch=value"),
                textBlock("Stable prompt"));

        assertThat(ClaudeCodeAttributionProcessor.systemWithoutAttribution(stringSystem)).isEqualTo("Stable prompt");
        assertThat(systemBlocks(ClaudeCodeAttributionProcessor.systemWithoutAttribution(blockSystem)))
                .extracting(MessageRequest.RequestTextBlock::getText)
                .containsExactly("Stable prompt");
    }

    @Test
    public void systemWithoutAttribution_doesNotStripNonFirstOrIncompleteText() {
        MessageRequest.RequestTextBlock prompt = textBlock("Stable prompt");
        MessageRequest.RequestTextBlock attribution = textBlock(ATTRIBUTION);
        List<MessageRequest.RequestTextBlock> nonFirst = Arrays.asList(prompt, attribution);
        String incomplete = "x-anthropic-billing-header: cc_version=version; cc_entrypoint=sdk-py;";

        assertThat(ClaudeCodeAttributionProcessor.systemWithoutAttribution(nonFirst)).isSameAs(nonFirst);
        assertThat(ClaudeCodeAttributionProcessor.systemWithoutAttribution(incomplete)).isEqualTo(incomplete);
        assertThat(ClaudeCodeAttributionProcessor.systemWithoutAttribution("prefix " + ATTRIBUTION)).isEqualTo("prefix " + ATTRIBUTION);
    }

    @Test
    public void systemWithoutAttribution_isIdempotentAndDoesNotStripNextBlock() {
        MessageRequest.RequestTextBlock first = textBlock(ATTRIBUTION);
        MessageRequest.RequestTextBlock second = textBlock(ATTRIBUTION);

        Object once = ClaudeCodeAttributionProcessor.systemWithoutAttribution(Arrays.asList(first, second));
        Object twice = ClaudeCodeAttributionProcessor.systemWithoutAttribution(once);

        assertThat(systemBlocks(once)).containsExactly(second);
        assertThat(systemBlocks(twice)).containsExactly(second);
    }

    @Test
    public void systemWithoutAttribution_rejectsOversizedField() {
        String oversized = "x-anthropic-billing-header: cc_version=" + String.join("", Collections.nCopies(129, "v"))
                + "; cc_entrypoint=sdk-py; cch=value;\nStable prompt";

        assertThat(ClaudeCodeAttributionProcessor.systemWithoutAttribution(oversized)).isEqualTo(oversized);
    }

    private MessageRequest.RequestTextBlock textBlock(String text) {
        return MessageRequest.RequestTextBlock.builder().type("text").text(text).build();
    }

    @SuppressWarnings("unchecked")
    private List<MessageRequest.RequestTextBlock> systemBlocks(MessageRequest request) {
        return (List<MessageRequest.RequestTextBlock>) request.getSystem();
    }

    @SuppressWarnings("unchecked")
    private List<MessageRequest.RequestTextBlock> systemBlocks(Object system) {
        return (List<MessageRequest.RequestTextBlock>) system;
    }
}
