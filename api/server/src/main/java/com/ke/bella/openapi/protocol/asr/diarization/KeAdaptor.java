package com.ke.bella.openapi.protocol.asr.diarization;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionRequest.AudioTranscriptionReq;
import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationResponse;
import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationProperty;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Component("KeSpeakerDiarization")
public class KeAdaptor implements SpeakerDiarizationAdaptor<SpeakerDiarizationProperty> {

    @Override
    public SpeakerDiarizationResponse speakerDiarization(AudioTranscriptionReq request, String url, SpeakerDiarizationProperty property) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request)));

        Request httpRequest = builder.build();
        return doRequest(httpRequest);
    }

    protected SpeakerDiarizationResponse doRequest(Request httpRequest) {
        return HttpUtils.httpRequest(httpRequest, SpeakerDiarizationResponse.class, ((response, httpResponse) -> {
            if(httpResponse.code() != 200 && response.getError() == null) {
                response.setError(OpenapiResponse.OpenapiError.builder()
                        .httpCode(httpResponse.code())
                        .message(httpResponse.message())
                        .type("HTTP_ERROR")
                        .build());
            }
        }));
    }

    @Override
    public String getDescription() {
        return "贝壳私有协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return SpeakerDiarizationProperty.class;
    }
}
