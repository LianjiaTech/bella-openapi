package com.ke.bella.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.ResponseHelper;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(TestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@Rollback
public class EndpointTest {
    @Autowired
    private MockMvc mockMvc;

    private static final String REQUEST_PATH = "function-chat-completion.json";

    @Value("${test.apikey:#{null}}")
    private String testApikey;


    @Test
    public void testParallelToolCallsAndSplitReasoningChatCompletions() throws Exception {
        // 读取请求体文件
        String requestBody = loadRequestBody();

        // 发起请求
        MvcResult mvcResult = mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + testApikey)
                        .header("X-BELLA-MOCK-REQUEST", "true")
                        .header("X-BELLA-SPLIT-REASONING", "true")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        // 收集并验证响应
        validateResponse(mvcResult);

    }

    @Test
    public void testChatCompletionMockTextNonStreamWithoutRealChannel() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mockChatCompletionRequest(false))
                        .header("Authorization", "Bearer " + testApikey)
                        .header("X-BELLA-MOCK-REQUEST", "true")
                        .header("X-BELLA-MOCK-TEXT", "hello%20world")
                        .header("X-BELLA-MOCK-TTLT", "10"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        Object asyncResult = mvcResult.getAsyncResult(120000);
        assertTrue(asyncResult instanceof CompletionResponse);
        CompletionResponse response = (CompletionResponse) asyncResult;
        CompletionResponse.Choice choice = response.getChoices().get(0);
        assertEquals("stop", choice.getFinish_reason());
        assertEquals("hello world", choice.content());
        assertNull(choice.getMessage().getTool_calls());
    }

    @Test
    public void testChatCompletionMockTextStreamWithoutRealChannel() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mockChatCompletionRequest(true))
                        .header("Authorization", "Bearer " + testApikey)
                        .header("X-BELLA-MOCK-REQUEST", "true")
                        .header("X-BELLA-MOCK-TEXT", "hello%20world")
                        .header("X-BELLA-MOCK-TTFT", "1")
                        .header("X-BELLA-MOCK-TTLT", "50")
                        .header("X-BELLA-MOCK-INTERVAL", "10")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        List<StreamCompletionResponse> responses = collectSseResponses(mvcResult, 120000);
        CompletionResponse finalResponse = mergeStreamResponses(responses);

        assertEquals("hello world", finalResponse.content());
        assertEquals("stop", finalResponse.finishReason());
    }

    private String loadRequestBody() throws Exception {
        ClassPathResource resource = new ClassPathResource(REQUEST_PATH);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    private String mockChatCompletionRequest(boolean stream) {
        return "{"
                + "\"model\":\"mock-test-model\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],"
                + "\"stream\":" + stream
                + "}";
    }

    private void validateResponse(MvcResult mvcResult) throws Exception {
        List<StreamCompletionResponse> responses = collectSseResponses(mvcResult, 120000);

        // 验证响应
        assertFalse(responses.isEmpty());

        // 合并所有响应
        CompletionResponse finalResponse = mergeStreamResponses(responses);

        // 验证响应内容
        assertNotNull(finalResponse);
        assertNotNull(finalResponse.reasoning());

        // 验证并行工具调用
        validateParallelToolCalls(finalResponse);

    }

    private CompletionResponse mergeStreamResponses(List<StreamCompletionResponse> responses) {
        CompletionResponse finalResponse = new CompletionResponse();
        Map<Integer, CompletionResponse.Choice> choiceBuffer = new HashMap<>();
        responses.forEach(streamResponse -> {
            ResponseHelper.overwrite(finalResponse, streamResponse);
            if(CollectionUtils.isEmpty(streamResponse.getChoices()) || streamResponse.getChoices().get(0).getDelta() == null) {
                return;
            }
            StreamCompletionResponse.Choice choice = streamResponse.getChoices().get(0);
            Integer choiceIndex = choice.getIndex();
            if(!choiceBuffer.containsKey(choiceIndex)) {
                choiceBuffer.put(choiceIndex, ResponseHelper.convert(choice));
            } else {
                ResponseHelper.combineMessage(choiceBuffer.get(choiceIndex).getMessage(), choice.getDelta());
                choiceBuffer.get(choiceIndex).setFinish_reason(choice.getFinish_reason());
            }
        });
        finalResponse.setChoices(Lists.newArrayList(choiceBuffer.values()));
        return finalResponse;
    }

    private void validateParallelToolCalls(CompletionResponse response) {
        assertNotNull(response.getChoices());
        assertFalse(response.getChoices().isEmpty());
        CompletionResponse.Choice choice = response.getChoices().get(0);
        assertNotNull(choice.getMessage().getTool_calls());
        assertFalse(choice.getMessage().getTool_calls().isEmpty());
        Map<String, Set<String>> idMap = new HashMap<>();
        Map<String, Set<Integer>> indexMap = new HashMap<>();
        for(Message.ToolCall toolCall : choice.getMessage().getTool_calls()) {
            assertNotNull(toolCall.getId());
            assertTrue(toolCall.getIndex() == 0 || toolCall.getIndex() == 1);
            assertNotNull(toolCall.getFunction());
            assertNotNull(toolCall.getFunction().getName());
            assertNotNull(toolCall.getFunction().getArguments());
            String name = toolCall.getFunction().getName();
            idMap.computeIfAbsent(name, k -> new HashSet<>());
            indexMap.computeIfAbsent(name, k -> new HashSet<>());
            idMap.get(name).add(toolCall.getId());
            indexMap.get(name).add(toolCall.getIndex());
        }
        assertTrue(idMap.values().stream().anyMatch(set -> set.size() == 2));
        assertTrue(indexMap.values().stream().anyMatch(set -> set.size() == 2));
    }

    private static List<StreamCompletionResponse> collectSseResponses(MvcResult mvcResult, int timeoutMillis)
            throws Exception {
        List<StreamCompletionResponse> responses = new ArrayList<>();
        MockHttpServletResponse response = mvcResult.getResponse();

        long startTime = System.currentTimeMillis();
        int processedLength = 0;
        boolean end = false;
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            String content = response.getContentAsString().replace("\r\n", "\n");
            int eventEnd;
            while ((eventEnd = content.indexOf("\n\n", processedLength)) >= 0) {
                String event = content.substring(processedLength, eventEnd);
                processedLength = eventEnd + 2;

                StringBuilder dataBuilder = new StringBuilder();
                for (String line : event.split("\n")) {
                    if(line.startsWith("data:")) {
                        if(dataBuilder.length() > 0) {
                            dataBuilder.append('\n');
                        }
                        dataBuilder.append(line.substring(5).trim());
                    }
                }
                if(dataBuilder.length() == 0) {
                    continue;
                }

                String data = dataBuilder.toString();
                if(data.equals("[DONE]")) {
                    end = true;
                    break;
                }
                StreamCompletionResponse streamResponse =
                        JacksonUtils.deserialize(data, StreamCompletionResponse.class);
                if(streamResponse == null) {
                    continue;
                }
                responses.add(streamResponse);
                if(streamResponse.getError() != null) {
                    throw new BellaException.ChannelException(response.getStatus(), "", "", streamResponse.getError());
                }
            }
            if(end) {
                break;
            }
            if(!mvcResult.getRequest().isAsyncStarted() && processedLength >= content.length()) {
                break;
            }
            Thread.sleep(50);
        }

        return responses;
    }
}
