package com.ke.bella.openapi.protocol.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.utils.DateTimeUtils;
import org.junit.Test;

public class RealTimeLogHandlerTest {

    @Test
    public void usesTranscriptionDurationForBillingWhenAvailable() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestTime(DateTimeUtils.getCurrentSeconds() - 10);
        processData.setTranscriptionDuration(3);

        new RealTimeLogHandler().process(processData);

        assertEquals(3L, processData.getUsage());
        assertTrue(processData.getDuration() >= 9);
    }

    @Test
    public void fallsBackToConnectionDurationWhenTranscriptionDurationIsUnavailable() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestTime(DateTimeUtils.getCurrentSeconds() - 10);

        new RealTimeLogHandler().process(processData);

        assertEquals(processData.getDuration(), processData.getUsage());
        assertTrue(processData.getDuration() >= 9);
    }
}
