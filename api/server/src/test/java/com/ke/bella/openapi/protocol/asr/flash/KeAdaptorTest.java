package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.protocol.asr.AsrRequest;
import okhttp3.Request;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KeAdaptorTest {
    private final KeAdaptor adaptor = new KeAdaptor();

    @Test
    public void buildHttpRequestSkipsBlankHotWordsHeader() {
        Request httpRequest = adaptor.buildHttpRequest(baseRequest(), "http://localhost/asr");

        assertNull(httpRequest.header("hot_words"));
        assertEquals("wav", httpRequest.header("format"));
        assertEquals("16000", httpRequest.header("sample_rate"));
        assertEquals("false", httpRequest.header("convert_numbers"));
    }

    @Test
    public void buildHttpRequestIncludesOptionalHotWordsHeader() {
        AsrRequest request = baseRequest();
        request.setHotWords("北京,上海");

        Request httpRequest = adaptor.buildHttpRequest(request, "http://localhost/asr");

        assertTrue(httpRequest.header("hot_words").contains("%E5%8C%97%E4%BA%AC"));
    }

    private static AsrRequest baseRequest() {
        return AsrRequest.builder()
                .content("audio".getBytes(StandardCharsets.UTF_8))
                .format("wav")
                .sampleRate(16000)
                .maxSentenceSilence(3000)
                .convertNumbers(false)
                .build();
    }
}
