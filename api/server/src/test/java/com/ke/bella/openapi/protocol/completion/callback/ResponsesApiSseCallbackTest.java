package com.ke.bella.openapi.protocol.completion.callback;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.completion.ResponsesApiStreamEvent;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
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
}
