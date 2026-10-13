package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.AudioFormat;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component("KeFlashAsr")
public class KeAdaptor implements FlashAsrAdaptor<AsrProperty> {
    @Override
    public FlashAsrResponse asr(AsrRequest request, String url, AsrProperty property, EndpointProcessData processData) {
        Request httpRequest = buildHttpRequest(request, url);
        clearLargeData(request);
        return HttpUtils.httpRequest(httpRequest, FlashAsrResponse.class);
    }

    Request buildHttpRequest(AsrRequest request, String url) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("format", request.getFormat())
                .header("max_sentence_silence", request.getMaxSentenceSilence().toString())
                .header("sample_rate", request.getSampleRate().toString())
                .header("convert_numbers", request.getConvertNumbers().toString())
                .post(RequestBody.create(MediaType.parse(AudioFormat.getContentType(request.getFormat())), request.getContent()));
        if(StringUtils.isNotBlank(request.getHotWords())) {
            builder.header("hot_words", UriUtils.encode(request.getHotWords(), StandardCharsets.UTF_8));
        }
        return builder.build();
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
