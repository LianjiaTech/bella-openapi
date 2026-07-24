package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HuoshanAdaptorTest {
    @Test
    void responseConverterUsesAudioEndTimeInsteadOfRequestLatency() {
        HuoshanAdaptor adaptor = new HuoshanAdaptor();
        EndpointProcessData processData = new EndpointProcessData();
        processData.setMetrics(new HashMap<>());
        processData.getMetrics().put("ttlt", 999999);
        CompletableFuture<List<String>> future = CompletableFuture.completedFuture(Arrays.asList(
                JacksonUtils.serialize(HuoshanAdaptor.Text.builder()
                        .beginTime(0)
                        .endTime(12000)
                        .text("first")
                        .build()),
                JacksonUtils.serialize(HuoshanAdaptor.Text.builder()
                        .beginTime(12000)
                        .endTime(34000)
                        .text("second")
                        .build())));

        FlashAsrResponse response = adaptor.responseConverter(future, processData);

        assertEquals(34000, response.getFlashResult().getDuration());
    }
}
