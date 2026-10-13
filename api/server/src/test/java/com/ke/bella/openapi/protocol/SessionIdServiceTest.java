package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.Message;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionIdServiceTest {

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void userHeaderTakesPriorityOverSessionsPromptCacheKeyAndPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Prompt-Cache-Id", "  user-id  ");
        request.addHeader("session-id", "codex-id");
        request.addHeader("x-claude-code-session-id", "claude-id");
        EndpointContext.setRequest(request);

        String value = SessionIdService.resolveForChat(chatRequest("body-id"), 64, 1);

        assertThat(value).isEqualTo("user-id");
        assertThat(EndpointContext.getProcessData().getSessionId()).isEqualTo("user-id");
    }

    @Test
    public void blankUserHeaderFallsBackToCodexThenClaudeCodeSession() {
        MockHttpServletRequest codexRequest = new MockHttpServletRequest();
        codexRequest.addHeader("X-Prompt-Cache-Id", " ");
        codexRequest.addHeader("session-id", "  codex-id  ");
        codexRequest.addHeader("x-claude-code-session-id", "claude-id");
        EndpointContext.setRequest(codexRequest);

        assertThat(SessionIdService.resolveForChat(chatRequest(null), 64, 1)).isEqualTo("codex-id");

        EndpointContext.clearAll();
        MockHttpServletRequest claudeCodeRequest = new MockHttpServletRequest();
        claudeCodeRequest.addHeader("X-Prompt-Cache-Id", " ");
        claudeCodeRequest.addHeader("session-id", "\t");
        claudeCodeRequest.addHeader("x-claude-code-session-id", "  claude-id  ");
        EndpointContext.setRequest(claudeCodeRequest);

        assertThat(SessionIdService.resolveForChat(chatRequest(null), 64, 1)).isEqualTo("claude-id");
    }

    @Test
    public void explicitPromptCacheKeyTakesPriorityOverPrefix() {
        String value = SessionIdService.resolveForChat(chatRequest("  body-id  "), 64, 1);

        assertThat(value).isEqualTo("body-id");
        assertThat(EndpointContext.getProcessData().getSessionId()).isEqualTo("body-id");
    }

    @Test
    public void promptPrefixCreatesFixedLengthHeaderSafeSessionId() {
        String value = SessionIdService.resolveForChat(chatRequest(null), 64, 1);

        assertThat(value).isEqualTo("19a7c4b1869812db5320cb0ebfad459ad482bb807fa0509176e2eb8a198defa2");
        assertThat(value).matches("[0-9a-f]{64}");
        assertThat(value).hasSize(64);
        assertThat(value).doesNotContain("stable prompt prefix");
        assertThat(EndpointContext.getProcessData().getSessionId()).isEqualTo(value);
    }

    @Test
    public void noCandidateReturnsNullAndDoesNotFallbackToAkCode() {
        EndpointProcessData processData = EndpointContext.getProcessData();
        processData.setAkCode("ak-default");
        CompletionRequest request = CompletionRequest.builder().build();

        assertThat(SessionIdService.resolveForChat(request, 64, 1)).isNull();
        assertThat(request.getPrompt_cache_key()).isNull();
        assertThat(processData.getSessionId()).isNull();
    }

    private CompletionRequest chatRequest(String promptCacheKey) {
        return CompletionRequest.builder()
                .prompt_cache_key(promptCacheKey)
                .messages(Collections.singletonList(Message.builder()
                        .role("system")
                        .content("stable prompt prefix")
                        .build()))
                .build();
    }
}
