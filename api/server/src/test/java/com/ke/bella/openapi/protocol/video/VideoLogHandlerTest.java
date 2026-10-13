package com.ke.bella.openapi.protocol.video;

import com.ke.bella.openapi.EndpointProcessData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class VideoLogHandlerTest {

    private final VideoLogHandler handler = new VideoLogHandler();

    @Test
    public void parsesQueueRawRequestAndSuccessfulResponse() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestRaw("{\"prompt\":\"a flying cat\",\"model\":\"video-model\",\"seconds\":\"5\",\"size\":\"1280x720\"}");
        processData.setResponseRaw("{\"status\":\"completed\",\"boundFileId\":\"file-123\","
                + "\"usage\":{\"duration\":5.2,\"size\":\"1280x720\",\"video_count\":1},"
                + "\"actualSeconds\":5.2,\"size\":\"1280x720\"}");

        handler.process(processData);

        VideoCreateRequest request = (VideoCreateRequest) processData.getRequest();
        assertEquals("a flying cat", request.getPrompt());
        assertEquals("video-model", request.getModel());
        assertEquals("5", request.getSeconds());
        assertEquals("1280x720", request.getSize());

        VideoJob response = (VideoJob) processData.getResponse();
        assertEquals(VideoJob.Status.completed.name(), response.getStatus());
        assertEquals("file-123", response.getBoundFileId());
        assertEquals(Double.valueOf(5.2), response.getActualSeconds());
        assertEquals("1280x720", response.getSize());
        assertNotNull(response.getUsage());
        assertEquals(Double.valueOf(5.2), response.getUsage().getDuration());
        assertEquals("1280x720", response.getUsage().getSize());
        assertEquals(Integer.valueOf(1), response.getUsage().getVideoCount());
        assertSame(response.getUsage(), processData.getUsage());
    }

    @Test
    public void parsesQueueFailureResponse() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setResponseRaw("{\"status\":\"failed\",\"error\":{\"code\":\"poll_timeout\",\"message\":\"Polling timed out\"}}");

        handler.process(processData);

        VideoJob response = (VideoJob) processData.getResponse();
        assertEquals(VideoJob.Status.failed.name(), response.getStatus());
        assertNotNull(response.getError());
        assertEquals("poll_timeout", response.getError().getCode());
        assertEquals("Polling timed out", response.getError().getMessage());
        assertNull(processData.getUsage());
    }

    @Test
    public void preservesExistingTypedValuesAndQueueUsage() {
        VideoCreateRequest request = VideoCreateRequest.builder()
                .prompt("typed request")
                .build();
        VideoJob response = VideoJob.builder()
                .status(VideoJob.Status.processing.name())
                .build();
        VideoUsage usage = VideoUsage.builder()
                .duration(3D)
                .build();

        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequest(request);
        processData.setResponse(response);
        processData.setUsage(usage);
        processData.setRequestRaw("{\"prompt\":\"raw request\"}");
        processData.setResponseRaw("{\"status\":\"completed\",\"usage\":{\"duration\":9}}");

        handler.process(processData);

        assertSame(request, processData.getRequest());
        assertSame(response, processData.getResponse());
        assertSame(usage, processData.getUsage());
    }

    @Test
    public void ignoresBlankAndInvalidRawPayloads() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestRaw(" ");
        processData.setResponseRaw("not-json");

        handler.process(processData);

        assertNull(processData.getRequest());
        assertNull(processData.getResponse());
        assertNull(processData.getUsage());
    }
}
