package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.AudioFormat;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component("KeFlashAsr")
public class KeAdaptor implements FlashAsrAdaptor<AsrProperty> {
    @Override
    public FlashAsrResponse asr(AsrRequest request, String url, AsrProperty property, EndpointProcessData processData) {
        Request httpRequest = new Request.Builder()
                .url(url)
                .header("format", request.getFormat())
                .header("max_sentence_silence", request.getMaxSentenceSilence().toString())
                .header("sample_rate", request.getSampleRate().toString())
                .header("hot_words", UriUtils.encode(request.getHotWords(), StandardCharsets.UTF_8))
                .post(RequestBody.create(MediaType.parse(AudioFormat.getContentType(request.getFormat())), request.getContent()))
                .build();
        clearLargeData(request);
        return HttpUtils.httpRequest(httpRequest, FlashAsrResponse.class);
    }

    @Override
    public String getDescription() {
        return "贝壳协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AsrProperty.class;
    }
}
