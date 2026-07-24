package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ResponsesLogHandlerTest {

    @Test
    public void processShouldKeepFullOutputAndOutputTextInProcessData() {
        ResponsesApiResponse response = baseResponse("completed");
        response.setOutput_text("direct summary");
        response.setOutput(Collections.singletonList(outputItem("full output")));

        EndpointProcessData processData = baseProcessData(response);
        new ResponsesLogHandler().process(processData);

        ResponsesApiResponse logResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals("resp_123", logResponse.getId());
        assertEquals("completed", logResponse.getStatus());
        assertEquals("direct summary", logResponse.getOutput_text());
        assertEquals(response.getUsage(), logResponse.getUsage());
        assertEquals(Collections.singletonList(outputItem("full output")), logResponse.getOutput());
    }

    @Test
    public void processShouldNotMutateOutputTextWhenMissing() {
        ResponsesApiResponse response = baseResponse("completed");
        response.setOutput(Arrays.asList(outputItem("first text"), outputItem("second text")));

        EndpointProcessData processData = baseProcessData(response);
        new ResponsesLogHandler().process(processData);

        ResponsesApiResponse logResponse = (ResponsesApiResponse) processData.getResponse();
        assertNull(logResponse.getOutput_text());
        assertEquals(response.getOutput(), logResponse.getOutput());
    }

    @Test
    public void processShouldAllowPendingResponseWithoutOutputSummary() {
        ResponsesApiResponse response = baseResponse("pending");
        response.setCreated(null);

        EndpointProcessData processData = baseProcessData(response);
        new ResponsesLogHandler().process(processData);

        ResponsesApiResponse logResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals("resp_123", logResponse.getId());
        assertEquals("pending", logResponse.getStatus());
        assertNull(logResponse.getOutput_text());
        assertNull(logResponse.getOutput());
    }

    @Test
    public void processShouldUseStreamingDurationForTtltAndKeepProtocolCreated() {
        ResponsesApiResponse response = baseResponse("completed");
        response.setCreated(100L);

        EndpointProcessData processData = baseProcessData(response);
        processData.setDuration(3L);
        processData.setResponseMillis(processData.getRequestMillis() + 3000L);
        new ResponsesLogHandler().process(processData);

        ResponsesApiResponse logResponse = (ResponsesApiResponse) processData.getResponse();
        assertEquals(100L, logResponse.getCreated().longValue());
        assertEquals(3, processData.getMetrics().get("ttlt"));
    }

    @Test
    public void processShouldKeepFunctionCallAndReasoningOutputInProcessData() {
        ResponsesApiResponse response = baseResponse("completed");
        ResponsesApiResponse.OutputItem functionCall = new ResponsesApiResponse.OutputItem();
        functionCall.setType("function_call");
        functionCall.setId("fc_123");
        functionCall.setCall_id("call_123");
        functionCall.setName("get_weather");
        functionCall.setArguments("{\"city\":\"Beijing\"}");

        ResponsesApiResponse.SummaryItem summaryItem = new ResponsesApiResponse.SummaryItem();
        summaryItem.setType("summary_text");
        summaryItem.setText("Need to check the weather");
        ResponsesApiResponse.OutputItem reasoning = new ResponsesApiResponse.OutputItem();
        reasoning.setType("reasoning");
        reasoning.setId("rs_123");
        reasoning.setSummary(Collections.singletonList(summaryItem));
        reasoning.setEncrypted_content("encrypted-reasoning");
        response.setOutput(Arrays.asList(outputItem("answer"), functionCall, reasoning));

        EndpointProcessData processData = baseProcessData(response);
        new ResponsesLogHandler().process(processData);

        ResponsesApiResponse logResponse = (ResponsesApiResponse) processData.getResponse();
        assertNull(logResponse.getOutput_text());
        assertEquals(3, logResponse.getOutput().size());
        assertEquals("get_weather", logResponse.getOutput().get(1).getName());
        assertEquals("{\"city\":\"Beijing\"}", logResponse.getOutput().get(1).getArguments());
        assertEquals("Need to check the weather", logResponse.getOutput().get(2).getSummary().get(0).getText());
        assertEquals("encrypted-reasoning", logResponse.getOutput().get(2).getEncrypted_content());
    }

    @Test
    public void processShouldNotMutateLargeOutputTextAndKeepFullOutput() {
        String largeText = StringUtils.repeat("x", 200_000);
        ResponsesApiResponse response = baseResponse("completed");
        response.setOutput(Collections.singletonList(outputItem(largeText)));

        EndpointProcessData processData = baseProcessData(response);
        new ResponsesLogHandler().process(processData);

        ResponsesApiResponse logResponse = (ResponsesApiResponse) processData.getResponse();
        assertNull(logResponse.getOutput_text());
        assertEquals(largeText, logResponse.getOutput().get(0).getContent().get(0).getText());
    }

    @Test
    public void processShouldUseResponseMillisWhenDurationRoundsToZero() {
        ResponsesApiResponse response = baseResponse("completed");
        response.setCreated(500L);

        EndpointProcessData processData = baseProcessData(response);
        processData.setDuration(0L);
        processData.setResponseMillis(processData.getRequestMillis() + 900L);
        new ResponsesLogHandler().process(processData);

        assertEquals(0, processData.getMetrics().get("ttlt"));
    }

    @Test
    public void responseModelShouldPreserveUnknownNestedProtocolFields() {
        String json = "{\"id\":\"resp_extra\",\"output\":[{\"type\":\"custom_tool_call\","
                + "\"provider_payload\":{\"trace\":\"abc\"},\"content\":[{\"type\":\"custom_content\","
                + "\"text\":\"answer\",\"provider_annotation\":\"kept\"}]}]}";

        ResponsesApiResponse response = JacksonUtils.deserialize(json, ResponsesApiResponse.class);
        String serialized = JacksonUtils.serialize(response);

        org.junit.Assert.assertTrue(serialized.contains("\"provider_payload\""));
        org.junit.Assert.assertTrue(serialized.contains("\"provider_annotation\":\"kept\""));
    }

    private static EndpointProcessData baseProcessData(ResponsesApiResponse response) {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestId("req_123");
        processData.setEndpoint("/v1/responses");
        processData.setRequestTime(100L);
        processData.setRequestMillis(100000L);
        processData.setResponse(response);
        return processData;
    }

    private static ResponsesApiResponse baseResponse(String status) {
        ResponsesApiResponse response = new ResponsesApiResponse();
        response.setId("resp_123");
        response.setObject("response");
        response.setCreated(200L);
        response.setModel("gpt-test");
        response.setStatus(status);
        response.setUsage(usage());
        return response;
    }

    private static ResponsesApiResponse.Usage usage() {
        ResponsesApiResponse.Usage usage = new ResponsesApiResponse.Usage();
        usage.setInput_tokens(10);
        usage.setOutput_tokens(20);
        usage.setTotal_tokens(30);
        return usage;
    }

    private static ResponsesApiResponse.OutputItem outputItem(String text) {
        ResponsesApiResponse.ContentItem contentItem = new ResponsesApiResponse.ContentItem();
        contentItem.setType("output_text");
        contentItem.setText(text);

        ResponsesApiResponse.OutputItem outputItem = new ResponsesApiResponse.OutputItem();
        outputItem.setType("message");
        outputItem.setContent(Collections.singletonList(contentItem));
        return outputItem;
    }
}
