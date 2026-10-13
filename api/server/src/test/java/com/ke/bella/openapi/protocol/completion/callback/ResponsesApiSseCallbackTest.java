package com.ke.bella.openapi.protocol.completion.callback;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiStreamEvent;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class ResponsesApiSseCallbackTest {

    @Test
    public void completedTerminalEventShouldRebuildMessageOutputFromTextDeltas() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);
        ResponsesApiResponse response = ResponsesApiResponse.builder()
                .id("resp_rebuilt")
                .status("completed")
                .output(Collections.emptyList())
                .build();

        callback.onEvent(null, "response.output_text.delta", "{\"delta\":\"hello \"}");
        callback.onEvent(null, "response.output_text.delta", "{\"delta\":\"world\"}");
        callback.onEvent(null, "response.completed", terminalEvent("response.completed", response));
        callback.onComplete();

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertNull(loggedResponse.getOutput_text());
        assertEquals(1, loggedResponse.getOutput().size());
        assertEquals("message", loggedResponse.getOutput().get(0).getType());
        assertEquals("assistant", loggedResponse.getOutput().get(0).getRole());
        assertEquals("output_text", loggedResponse.getOutput().get(0).getContent().get(0).getType());
        assertEquals("hello world", loggedResponse.getOutput().get(0).getContent().get(0).getText());
        verify(logger).log(processData);
    }

    @Test
    public void completedTerminalEventShouldRebuildFunctionCallAndReasoningItems() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);

        ResponsesApiResponse.OutputItem functionCall = new ResponsesApiResponse.OutputItem();
        functionCall.setType("function_call");
        functionCall.setId("fc_123");
        functionCall.setCall_id("call_123");
        functionCall.setName("get_weather");
        functionCall.setArguments("{\"city\":\"Beijing\"}");
        ResponsesApiResponse.SummaryItem summary = new ResponsesApiResponse.SummaryItem();
        summary.setType("summary_text");
        summary.setText("Check weather");
        ResponsesApiResponse.OutputItem reasoning = new ResponsesApiResponse.OutputItem();
        reasoning.setType("reasoning");
        reasoning.setId("rs_123");
        reasoning.setSummary(Collections.singletonList(summary));

        callback.onEvent(null, "response.output_item.done", outputItemEvent(0, functionCall));
        callback.onEvent(null, "response.output_item.done", outputItemEvent(1, reasoning));
        callback.onEvent(null, "response.completed", terminalEvent("response.completed",
                ResponsesApiResponse.builder().id("resp_tools").status("completed").output(Collections.emptyList()).build()));
        callback.onComplete();

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals(2, loggedResponse.getOutput().size());
        assertEquals("get_weather", loggedResponse.getOutput().get(0).getName());
        assertEquals("{\"city\":\"Beijing\"}", loggedResponse.getOutput().get(0).getArguments());
        assertEquals("Check weather", loggedResponse.getOutput().get(1).getSummary().get(0).getText());
        verify(logger).log(processData);
    }

    @Test
    public void outputItemEventsShouldRebuildToolSearchCallWithObjectArguments() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);
        String addedItem = "{\"type\":\"tool_search_call\",\"id\":\"ts_123\",\"status\":\"in_progress\","
                + "\"arguments\":{\"query\":\"initial query\"}}";
        String doneItem = "{\"type\":\"tool_search_call\",\"id\":\"ts_123\",\"status\":\"completed\","
                + "\"arguments\":{\"query\":\"node_repl js persistent browser control\"}}";

        callback.onEvent(null, "response.output_item.added",
                "{\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":" + addedItem + "}");

        assertEquals(1, callback.outputItems.size());
        assertEquals("{\"query\":\"initial query\"}", callback.outputItems.get(0).getArguments());

        callback.onEvent(null, "response.output_item.done",
                "{\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":" + doneItem + "}");
        callback.onEvent(null, "response.completed", terminalEvent("response.completed",
                ResponsesApiResponse.builder().id("resp_tool_search").status("completed").output(Collections.emptyList()).build()));
        callback.onComplete();

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals(1, loggedResponse.getOutput().size());
        assertEquals("tool_search_call", loggedResponse.getOutput().get(0).getType());
        assertEquals("{\"query\":\"node_repl js persistent browser control\"}",
                loggedResponse.getOutput().get(0).getArguments());
        verify(logger).log(processData);
    }

    @Test
    public void completedTerminalEventShouldParseToolSearchCallArgumentsAndUsage() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);
        String eventData = "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_completed_tool_search\","
                + "\"status\":\"completed\",\"output\":[{\"type\":\"tool_search_call\",\"status\":\"completed\","
                + "\"arguments\":{\"query\":\"node_repl js persistent browser control\"}}],"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5,\"total_tokens\":15}}}";

        callback.onEvent(null, "response.completed", eventData);
        callback.onComplete();

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals("{\"query\":\"node_repl js persistent browser control\"}",
                loggedResponse.getOutput().get(0).getArguments());
        assertNotNull(loggedResponse.getUsage());
        assertEquals(Integer.valueOf(10), loggedResponse.getUsage().getInput_tokens());
        assertEquals(Integer.valueOf(5), loggedResponse.getUsage().getOutput_tokens());
        assertEquals(Integer.valueOf(15), loggedResponse.getUsage().getTotal_tokens());
        verify(logger).log(processData);
    }

    @Test
    public void completedTerminalEventShouldRebuildItemsFromArgumentAndReasoningDeltas() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);

        ResponsesApiResponse.OutputItem functionCall = new ResponsesApiResponse.OutputItem();
        functionCall.setType("function_call");
        functionCall.setId("fc_delta");
        functionCall.setCall_id("call_delta");
        functionCall.setName("get_weather");
        ResponsesApiResponse.OutputItem reasoning = new ResponsesApiResponse.OutputItem();
        reasoning.setType("reasoning");
        reasoning.setId("rs_delta");
        callback.onEvent(null, "response.output_item.added", outputItemEvent("response.output_item.added", 0, functionCall));
        callback.onEvent(null, "response.output_item.added", outputItemEvent("response.output_item.added", 1, reasoning));
        callback.onEvent(null, "response.function_call_arguments.delta",
                deltaEvent("response.function_call_arguments.delta", 0, "fc_delta", null, "{\"city\":"));
        callback.onEvent(null, "response.function_call_arguments.delta",
                deltaEvent("response.function_call_arguments.delta", 0, "fc_delta", null, "\"Beijing\"}"));
        callback.onEvent(null, "response.reasoning_summary_text.delta",
                deltaEvent("response.reasoning_summary_text.delta", 1, "rs_delta", 0, "Check "));
        callback.onEvent(null, "response.reasoning_summary_text.delta",
                deltaEvent("response.reasoning_summary_text.delta", 1, "rs_delta", 0, "weather"));
        callback.onEvent(null, "response.completed", terminalEvent("response.completed",
                ResponsesApiResponse.builder().id("resp_deltas").status("completed").output(Collections.emptyList()).build()));
        callback.onComplete();

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals("{\"city\":\"Beijing\"}", loggedResponse.getOutput().get(0).getArguments());
        assertEquals("Check weather", loggedResponse.getOutput().get(1).getSummary().get(0).getText());
        verify(logger).log(processData);
    }

    @Test
    public void completedTerminalEventShouldKeepProtocolOutputAndOutputText() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);
        ResponsesApiResponse.ContentItem content = new ResponsesApiResponse.ContentItem();
        content.setType("output_text");
        content.setText("complete answer");
        ResponsesApiResponse.OutputItem outputItem = new ResponsesApiResponse.OutputItem();
        outputItem.setType("message");
        outputItem.setRole("assistant");
        outputItem.setContent(Collections.singletonList(content));
        ResponsesApiResponse response = ResponsesApiResponse.builder()
                .id("resp_completed")
                .status("completed")
                .output_text("complete answer summary")
                .output(Collections.singletonList(outputItem))
                .build();

        callback.onEvent(null, "response.output_text.delta", "{\"delta\":\"complete answer\"}");
        callback.onEvent(null, "response.completed", terminalEvent("response.completed", response));
        callback.onComplete();

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals("complete answer", loggedResponse.getOutput().get(0).getContent().get(0).getText());
        assertEquals("complete answer summary", loggedResponse.getOutput_text());
        verify(logger).log(processData);
    }

    @Test
    public void failedTerminalEventShouldBeRecordedInEndpointLog() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);
        ResponsesApiResponse response = ResponsesApiResponse.builder()
                .id("resp_failed")
                .status("failed")
                .error(new OpenapiResponse.OpenapiError("upstream_error", "failed", 502))
                .build();

        callback.onEvent(null, "response.failed", terminalEvent("response.failed", response));
        callback.onComplete();

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals("resp_failed", loggedResponse.getId());
        assertEquals("failed", loggedResponse.getStatus());
        assertNotNull(loggedResponse.getError());
        assertEquals("failed", loggedResponse.getError().getMessage());
        verify(logger).log(processData);
    }

    @Test
    public void safetyBlockShouldRecordAndSendFailedResponseWithTopLevelSensitive() {
        EndpointProcessData processData = processData();
        processData.setModel("gpt-test");
        EndpointLogger logger = mock(EndpointLogger.class);
        TestSseEmitter sse = new TestSseEmitter();
        Map<String, Object> sensitive = new HashMap<>();
        sensitive.put("msg", "block");
        sensitive.put("trace_id", "trace_123");
        sensitive.put("results", new HashMap<>());
        ResponsesApiSseCallback callback = new ResponsesApiSseCallback(sse, processData, apikeyInfo(), logger,
                (request, isMock) -> {
                    throw new BellaException.SafetyCheckException(sensitive);
                });

        callback.onEvent(null, "response.created", terminalEvent("response.created",
                ResponsesApiResponse.builder().id("resp_safety").status("in_progress").build()));
        callback.onEvent(null, "response.output_text.delta",
                deltaEvent("response.output_text.delta", 0, null, null, "blocked text"));
        callback.onEvent(null, "response.completed", terminalEvent("response.completed",
                ResponsesApiResponse.builder().id("resp_safety").status("completed").output(Collections.emptyList()).build()));

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals("resp_safety", loggedResponse.getId());
        assertEquals("failed", loggedResponse.getStatus());
        assertEquals("safety_check", loggedResponse.getError().getType());
        assertEquals("内容未通过安全检查", loggedResponse.getError().getMessage());
        assertSame(sensitive, loggedResponse.getSensitive());
        assertTrue(sse.sentData.stream().anyMatch(data -> data.contains("\"sensitive\"") && data.contains("\"msg\":\"block\"")));
        verify(logger).log(processData);
    }

    @Test
    public void incompleteTerminalEventShouldBeRecordedInEndpointLog() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);
        ResponsesApiResponse response = ResponsesApiResponse.builder()
                .id("resp_incomplete")
                .status("incomplete")
                .output_text("partial answer")
                .build();

        callback.onEvent(null, "response.incomplete", terminalEvent("response.incomplete", response));
        callback.onComplete();

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals("resp_incomplete", loggedResponse.getId());
        assertEquals("incomplete", loggedResponse.getStatus());
        assertEquals("partial answer", loggedResponse.getOutput_text());
        assertEquals("partial answer", loggedResponse.getOutput().get(0).getContent().get(0).getText());
        verify(logger).log(processData);
    }

    @Test
    public void firstDataEventShouldSetFirstPackageTimeWithoutCreatedEvent() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);

        callback.onEvent(null, "response.output_text.delta", "{\"delta\":\"hello\"}");
        callback.onComplete();

        org.junit.Assert.assertTrue(processData.getFirstPackageTime() >= processData.getRequestMillis());
        org.junit.Assert.assertTrue(processData.getResponseMillis() >= processData.getFirstPackageTime());
        verify(logger).log(processData);
    }

    private static ResponsesApiSseCallback callback(EndpointProcessData processData, EndpointLogger logger) {
        return new ResponsesApiSseCallback(null, processData, null, logger, null);
    }

    private static ApikeyInfo apikeyInfo() {
        return ApikeyInfo.builder()
                .code("ak_code")
                .safetySceneCode("scene_code")
                .serviceId("service_id")
                .build();
    }

    private static EndpointProcessData processData() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestId("req_123");
        processData.setRequestTime(System.currentTimeMillis() / 1000);
        processData.setRequestMillis(System.currentTimeMillis());
        return processData;
    }

    private static String terminalEvent(String type, ResponsesApiResponse response) {
        return JacksonUtils.serialize(ResponsesApiStreamEvent.builder()
                .type(type)
                .response(response)
                .build());
    }

    private static String outputItemEvent(int outputIndex, ResponsesApiResponse.OutputItem item) {
        return outputItemEvent("response.output_item.done", outputIndex, item);
    }

    private static String outputItemEvent(String type, int outputIndex, ResponsesApiResponse.OutputItem item) {
        return JacksonUtils.serialize(ResponsesApiStreamEvent.builder()
                .type(type)
                .output_index(outputIndex)
                .item(item)
                .build());
    }

    private static String deltaEvent(String type, int outputIndex, String itemId, Integer summaryIndex, String delta) {
        return JacksonUtils.serialize(ResponsesApiStreamEvent.builder()
                .type(type)
                .output_index(outputIndex)
                .item_id(itemId)
                .summary_index(summaryIndex)
                .delta(delta)
                .build());
    }

    private static class TestSseEmitter extends SseEmitter {
        private final List<String> sentData = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            Set<ResponseBodyEmitter.DataWithMediaType> dataSet = builder.build();
            for (ResponseBodyEmitter.DataWithMediaType dataWithMediaType : dataSet) {
                sentData.add(String.valueOf(dataWithMediaType.getData()));
            }
        }
    }

    @Test
    public void completedTerminalEventShouldNormalizeXToolsUsage() {
        EndpointProcessData processData = processData();
        EndpointLogger logger = mock(EndpointLogger.class);
        ResponsesApiSseCallback callback = callback(processData, logger);
        String eventData = "{\"type\":\"response.completed\",\"response\":{"
                + "\"id\":\"resp_x_tools\",\"status\":\"completed\","
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5,"
                + "\"x_tools\":{\"web_search\":{\"count\":3}}}}}";

        callback.onEvent(null, "response.completed", eventData);
        callback.onComplete();

        ResponsesApiResponse loggedResponse = (ResponsesApiResponse) processData.getResponse();
        assertNotNull(loggedResponse.getUsage());
        assertEquals(Integer.valueOf(3), loggedResponse.getUsage().getTool_usage().get("web_search"));
        verify(logger).log(processData);
    }
}
