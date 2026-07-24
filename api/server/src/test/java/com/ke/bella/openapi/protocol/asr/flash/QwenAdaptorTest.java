package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QwenAdaptorTest {
    @Test
    void convertsUsageSecondsToFlashResultDurationMillis() {
        QwenAdaptor adaptor = new QwenAdaptor();
        QwenFlashAsrResponse response = QwenFlashAsrResponse.builder()
                .usage(QwenFlashAsrResponse.Usage.builder()
                        .seconds(42)
                        .build())
                .build();

        FlashAsrResponse converted = adaptor.convertToFlashAsrResponse(response, new EndpointProcessData());

        assertEquals(42000, converted.getFlashResult().getDuration());
    }
}
