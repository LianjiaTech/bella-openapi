package com.ke.bella.openapi.protocol.video;

import com.ke.bella.openapi.JsonSchema;
import com.ke.bella.openapi.protocol.IPriceInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoEndpointMetadataTest {
    @Test
    void videoEndpointProvidesPriceInfoSchema() {
        Class<? extends IPriceInfo> priceInfoType = IPriceInfo.EndpointPriceInfoType.fetchType("/v1/videos");

        assertEquals(VideoPriceInfo.class, priceInfoType);
        Set<String> fields = JsonSchema.toSchema(priceInfoType).getParams().stream()
                .map(schema -> schema.getCode())
                .collect(Collectors.toSet());
        assertEquals(new HashSet<>(Arrays.asList("input", "output")), fields);
    }
}
