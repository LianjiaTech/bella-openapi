package com.ke.bella.openapi.protocol.video;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Wan3VideoConverterTest {
    @Test
    public void mapsWanStatusesAndUsage() {
        Wan3VideoResponse response = new Wan3VideoResponse();
        Wan3VideoResponse.Output output = new Wan3VideoResponse.Output();
        output.setTaskStatus("SUCCEEDED");
        output.setVideoUrl("https://example.test/video.mp4");
        response.setOutput(output);
        Wan3VideoResponse.Usage usage = new Wan3VideoResponse.Usage();
        usage.setDuration(30D);
        usage.setFps(30);
        response.setUsage(usage);

        ChannelVideoResult result = Wan3VideoConverter.convertFromWanQuery("task-id", response);

        assertEquals(VideoJob.Status.completed.name(), result.getStatus());
        assertEquals(Double.valueOf(30D), result.getActualSeconds());
        assertEquals(Integer.valueOf(30), result.getUsage().getFps());
        assertEquals("https://example.test/video.mp4", Wan3VideoConverter.resolveVideoUrl(response));
    }

    @Test
    public void mapsCanceledAndUnknownToExistingStatuses() {
        for(String upstream : new String[] {"CANCELED", "UNKNOWN"}) {
            Wan3VideoResponse response = new Wan3VideoResponse();
            Wan3VideoResponse.Output output = new Wan3VideoResponse.Output();
            output.setTaskStatus(upstream);
            response.setOutput(output);
            ChannelVideoResult result = Wan3VideoConverter.convertFromWanQuery("task-id", response);
            assertEquals(upstream.equals("CANCELED") ? VideoJob.Status.cancelled.name() : VideoJob.Status.failed.name(), result.getStatus());
        }
    }
}
