package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.ke.bella.openapi.protocol.asr.HuoshanProperty;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Component("HuoshanBigModelFlashAsr")
public class HuoshanBigModelFlashAsr implements FlashAsrAdaptor<HuoshanProperty> {
    static final String DEFAULT_RESOURCE_ID = "volc.bigasr.auc_turbo";
    static final String DEFAULT_MODEL_NAME = "bigmodel";
    static final String SUCCESS_STATUS_CODE = "20000000";

    @Override
    public FlashAsrResponse asr(AsrRequest request, String url, HuoshanProperty property, EndpointProcessData processData) {
        try {
            HuoshanBigModelFlashAsrRequest huoshanRequest = buildHuoshanRequest(request, property);
            Request httpRequest = buildHttpRequest(huoshanRequest, url, property, processData);
            clearLargeData(request);
            try (Response response = HttpUtils.httpRequest(httpRequest)) {
                String body = response.body() == null ? null : response.body().string();
                HuoshanBigModelFlashAsrResponse huoshanResponse = JacksonUtils.deserialize(body, HuoshanBigModelFlashAsrResponse.class);
                validateHuoshanStatus(response, huoshanResponse);
                String channelRequestId = channelRequestId(response, huoshanResponse);
                if(StringUtils.isNotBlank(channelRequestId)) {
                    processData.setChannelRequestId(channelRequestId);
                }
                return convertToFlashAsrResponse(huoshanResponse, processData);
            }
        } catch (IOException e) {
            throw BellaException.fromException(e);
        } catch (Exception e) {
            throw BellaException.fromException(e);
        }
    }

    HuoshanBigModelFlashAsrRequest buildHuoshanRequest(AsrRequest request, HuoshanProperty property) {
        HuoshanBigModelFlashAsrRequest.User user = HuoshanBigModelFlashAsrRequest.User.builder()
                .uid(resolveUid(request, property))
                .build();
        HuoshanBigModelFlashAsrRequest.Audio audio = HuoshanBigModelFlashAsrRequest.Audio.builder()
                .format(request.getFormat())
                .codec(request.getCodec())
                .rate(request.getSampleRate())
                .bits(request.getBits())
                .channel(request.getChannel())
                .data(Base64.getEncoder().encodeToString(request.getContent()))
                .build();
        HuoshanBigModelFlashAsrRequest.RequestOptions options = HuoshanBigModelFlashAsrRequest.RequestOptions.builder()
                .modelName(DEFAULT_MODEL_NAME)
                .enablePunc(request.getEnablePunc())
                .enableItn(request.getEnableItn() != null ? request.getEnableItn() : request.getConvertNumbers())
                .enableSpeakerInfo(request.getEnableSpeakerInfo())
                .enableDdc(request.getEnableDdc())
                .outputZhVariant(request.getOutputZhVariant())
                .enableAutoLang(request.getEnableAutoLang())
                .showUtterances(request.getShowUtterances())
                .resultType(request.getResultType())
                .enableAccelerateText(request.getEnableAccelerateText())
                .accelerateScore(request.getAccelerateScore())
                .vadSegmentDuration(request.getVadSegmentDuration())
                .endWindowSize(request.getEndWindowSize())
                .forceToSpeechTime(request.getForceToSpeechTime())
                .sensitiveWordsFilter(request.getSensitiveWordsFilter())
                .enablePoiFc(request.getEnablePoiFc())
                .enableMusicFc(request.getEnableMusicFc())
                .boostingTableName(request.getBoostingTableName())
                .correctTableName(request.getCorrectTableName())
                .correctTableId(request.getCorrectTableId())
                .hotWords(request.getHotWords())
                .hotWordsTableId(request.getHotWordsTableId())
                .language(request.getLanguage())
                .context(request.getContext())
                .build();
        return HuoshanBigModelFlashAsrRequest.builder()
                .user(user)
                .audio(audio)
                .request(options)
                .build();
    }

    Request buildHttpRequest(HuoshanBigModelFlashAsrRequest huoshanRequest, String url, HuoshanProperty property,
            EndpointProcessData processData) {
        String requestId = requestId(processData);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("X-Api-Resource-Id", StringUtils.defaultIfBlank(property.getDeployName(), DEFAULT_RESOURCE_ID))
                .header("X-Api-Request-Id", requestId)
                .header("X-Api-Sequence", "-1")
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.serialize(huoshanRequest)));
        applyAuth(builder, property);
        return builder.build();
    }

    FlashAsrResponse convertToFlashAsrResponse(HuoshanBigModelFlashAsrResponse huoshanResponse, EndpointProcessData processData) {
        List<FlashAsrResponse.Sentence> sentences = new ArrayList<>();
        if(huoshanResponse != null && huoshanResponse.getResult() != null) {
            if(!CollectionUtils.isEmpty(huoshanResponse.getResult().getUtterances())) {
                for (HuoshanBigModelFlashAsrResponse.Utterance utterance : huoshanResponse.getResult().getUtterances()) {
                    sentences.add(FlashAsrResponse.Sentence.builder()
                            .beginTime(utterance.effectiveBeginTime())
                            .endTime(utterance.effectiveEndTime())
                            .text(utterance.getText())
                            .build());
                }
            } else if(StringUtils.isNotBlank(huoshanResponse.getResult().getText())) {
                sentences.add(FlashAsrResponse.Sentence.builder()
                        .beginTime(0L)
                        .endTime(huoshanResponse.duration())
                        .text(huoshanResponse.getResult().getText())
                        .build());
            }
        }
        FlashAsrResponse.FlashResult flashResult = FlashAsrResponse.FlashResult.builder()
                .duration(huoshanResponse == null ? 0 : huoshanResponse.duration())
                .sentences(sentences)
                .build();
        return FlashAsrResponse.builder()
                .taskId(processData.getChannelRequestId())
                .user(processData.getUser())
                .flashResult(flashResult)
                .build();
    }

    @Override
    public String getDescription() {
        return "火山大模型录音文件极速版一句话 ASR";
    }

    @Override
    public Class<?> getPropertyClass() {
        return HuoshanProperty.class;
    }

    private void applyAuth(Request.Builder builder, HuoshanProperty property) {
        AuthorizationProperty auth = property.getAuth();
        if(StringUtils.isNotBlank(property.getAppid())) {
            String accessKey = auth == null ? null : StringUtils.defaultIfBlank(auth.getSecret(), auth.getApiKey());
            if(StringUtils.isBlank(accessKey)) {
                throw new BellaException.ChannelException(503, "Service Unavailable",
                        "Huoshan legacy BigModel flash ASR requires auth.secret/auth.apiKey");
            }
            builder.header("X-Api-App-Key", property.getAppid());
            builder.header("X-Api-Access-Key", accessKey);
            return;
        }
        String apiKey = auth == null ? null : auth.getApiKey();
        if(StringUtils.isBlank(apiKey)) {
            throw new BellaException.ChannelException(503, "Service Unavailable",
                    "Huoshan BigModel flash ASR requires auth.apiKey for X-Api-Key");
        }
        builder.header("X-Api-Key", apiKey);
    }

    private void validateHuoshanStatus(Response response, HuoshanBigModelFlashAsrResponse huoshanResponse) {
        String statusCode = response.header("X-Api-Status-Code");
        if(SUCCESS_STATUS_CODE.equals(statusCode)) {
            return;
        }
        String message = StringUtils.defaultIfBlank(response.header("X-Api-Message"), response.header("X-Api-Status-Message"));
        if(StringUtils.isBlank(message) && huoshanResponse != null) {
            message = huoshanResponse.getMessage();
        }
        if(StringUtils.isBlank(message)) {
            message = response.message();
        }
        String logId = channelRequestId(response, huoshanResponse);
        String error = String.format("Huoshan BigModel flash ASR failed: status_code=%s, message=%s, logid=%s",
                statusCode, message, logId);
        throw new BellaException.ChannelException(response.code() >= 400 ? response.code() : 503, "Service Unavailable", error);
    }

    private String channelRequestId(Response response, HuoshanBigModelFlashAsrResponse huoshanResponse) {
        String logId = StringUtils.defaultIfBlank(response.header("X-Tt-Logid"), response.header("X-Api-Log-Id"));
        logId = StringUtils.defaultIfBlank(logId, response.header("X-Api-Request-Id"));
        if(StringUtils.isBlank(logId) && huoshanResponse != null) {
            logId = huoshanResponse.getLogid();
        }
        return logId;
    }

    private String requestId(EndpointProcessData processData) {
        if(processData != null && StringUtils.isNotBlank(processData.getRequestId())) {
            return processData.getRequestId();
        }
        return UUID.randomUUID().toString();
    }

    private String resolveUid(AsrRequest request, HuoshanProperty property) {
        String uid = request.getUid();
        if(StringUtils.isNotBlank(uid)) {
            return uid;
        }
        if(StringUtils.isNotBlank(property.getAppid())) {
            return property.getAppid();
        }
        AuthorizationProperty auth = property.getAuth();
        return auth == null ? null : auth.getApiKey();
    }
}
