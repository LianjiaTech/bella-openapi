package com.ke.bella.openapi.utils;

import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagesEditRequestUtilsTest {

    @Test
    void readFromMultipartRequest_ParsesOfficialFieldsAndExtraValues() {
        MockMultipartHttpServletRequest multipartRequest = new MockMultipartHttpServletRequest();
        multipartRequest.addParameter("prompt", "edit prompt");
        multipartRequest.addParameter("quality", "low");
        multipartRequest.addParameter("output_compression", "80");
        multipartRequest.addParameter("custom_flag", "true");
        multipartRequest.addParameter("custom_array", "one", "two");

        ImagesEditRequest result = ImagesEditRequestUtils.readFromMultipartRequest(multipartRequest);

        assertEquals("low", result.getQuality());
        assertEquals(80, result.getOutput_compression());
        assertEquals(true, result.getExtra_body().get("custom_flag"));
        assertTrue(result.getExtra_body().get("custom_array") instanceof Object[]);
        assertEquals("one", ((Object[]) result.getExtra_body().get("custom_array"))[0]);
        assertEquals("two", ((Object[]) result.getExtra_body().get("custom_array"))[1]);
    }
}
