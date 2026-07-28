package com.ke.bella.openapi.protocol.video;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class QwenVideoConverterTest {

    @Test
    public void convertFromQwenQueryMapsOfficialUsageFields() {
        QwenVideoResponse response = new QwenVideoResponse();
        QwenVideoResponse.Output output = new QwenVideoResponse.Output();
        output.setTaskStatus("SUCCEEDED");
        response.setOutput(output);

        QwenVideoResponse.Usage usage = new QwenVideoResponse.Usage();
        usage.setDuration(5D);
        usage.setSize("1280*720");
        usage.setFps(24);
        usage.setVideoCount(1);
        usage.setAudio(false);
        usage.setSr("720");
        response.setUsage(usage);

        ChannelVideoResult result = QwenVideoConverter.convertFromQwenQuery("task-id", response);

        assertEquals(VideoJob.Status.completed.name(), result.getStatus());
        assertEquals(Double.valueOf(5D), result.getActualSeconds());
        assertEquals("1280*720", result.getSize());
        assertEquals(Double.valueOf(5D), result.getUsage().getDuration());
        assertEquals("1280*720", result.getUsage().getSize());
        assertEquals(Integer.valueOf(24), result.getUsage().getFps());
        assertEquals(Integer.valueOf(1), result.getUsage().getVideoCount());
        assertEquals(false, result.getUsage().getAudio());
        assertEquals("720", result.getUsage().getSr());
    }

    @Test(expected = com.ke.bella.openapi.common.exception.BellaException.ChannelException.class)
    public void convertFromQwenQueryRejectsMissingOutput() {
        QwenVideoConverter.convertFromQwenQuery("task-id", new QwenVideoResponse());
    }

}
