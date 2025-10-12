package com.ke.bella.openapi.protocol.tts;

import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.BellaWebSocketListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@Component("HuoShanTts")
public class HuoShanAdaptor implements TtsAdaptor<HuoShanProperty> {
    @Autowired
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    @Override
    public byte[] tts(TtsRequest request, String url, HuoShanProperty property){
        HuoShanRequest huoShanRequest = convertTtsRequestToHuoShanRequest(request, property);
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(huoShanRequest)));
        Request httpRequest = builder.build();
        clearLargeData(request, huoShanRequest);
        return processHuoShanResponse(httpRequest);
    }

    @Override
    public void streamTts(TtsRequest request, String url, HuoShanProperty property, Callbacks.StreamCallback callback) {
        Request webSocketRequest = new Request.Builder()
                .url(property.websocketUrl)
                .header("X-Api-App-Key", property.appId)
                .header("X-Api-Access-Key", property.auth.getSecret())
                .header("X-Api-Resource-Id", property.resourceId)
                .header("X-Api-Connect-Id", UUID.randomUUID().toString())
                .build();
        HttpUtils.websocketRequest(webSocketRequest, new BellaWebSocketListener((Callbacks.WebSocketCallback) callback));
    }

    @Override
    public Callbacks.StreamCallback buildCallback(TtsRequest request, Callbacks.Sender byteSender,
            EndpointProcessData processData, EndpointLogger logger) {
        return new HuoshanStreamTtsCallback(request, byteSender, EndpointContext.getProcessData(), logger);
    }

    private HuoShanRequest convertTtsRequestToHuoShanRequest(TtsRequest ttsRequest, HuoShanProperty property) {
        HuoShanRequest.App app = HuoShanRequest.App.builder()
                .appId(property.getAppId())
                .token(property.getAuth().getApiKey().substring(property.getAuth().getApiKey().indexOf(";") + 1))
                .cluster(property.getCluster())
                .build();

        HuoShanRequest.User user = HuoShanRequest.User.builder()
                .uid("uid")
                .build();

        HuoShanRequest.Audio audio = HuoShanRequest.Audio.builder()
                .voiceType(ttsRequest.getVoice() != null ? ttsRequest.getVoice() : "BV001_streaming")
                .encoding(ttsRequest.getResponseFormat())
                .speedRatio(ttsRequest.getSpeed() != null ? ttsRequest.getSpeed() : 1.0)
                .build();

        HuoShanRequest.TextRequest request = HuoShanRequest.TextRequest.builder()
                .reqId(String.valueOf(UUID.randomUUID()))
                .text(ttsRequest.getInput())
                .operation("query")
                .build();

        return HuoShanRequest.builder()
                .app(app)
                .user(user)
                .audio(audio)
                .request(request)
                .build();
    }

    private byte[] processHuoShanResponse(Request httpRequest) {
        HuoShanResponse huoshanResponse = HttpUtils.httpRequest(httpRequest, HuoShanResponse.class);
        if (huoshanResponse == null || huoshanResponse.getCode()!= HuoShanResponseCodeEnum.OK.code || huoshanResponse.getData() == null) {
            HttpStatus status = getHttpStatus(HuoShanAdaptor.HuoShanResponseCodeEnum.getByCode(huoshanResponse == null ? HuoShanResponseCodeEnum.OTHER_ERROR.code : huoshanResponse.getCode()));
            throw new ChannelException.OpenAIException(status.value(), status.getReasonPhrase(), huoshanResponse == null ? HuoShanResponseCodeEnum.OTHER_ERROR.message : huoshanResponse.getMessage());
        }
        return BASE64_DECODER.decode(huoshanResponse.getData());
    }

    private HttpStatus getHttpStatus(HuoShanResponseCodeEnum responseCode) {
        switch (responseCode) {
            case INVALID_REQUEST:
                return HttpStatus.BAD_REQUEST;
            case CONCURRENT_LIMIT_EXCEEDED:
                return HttpStatus.TOO_MANY_REQUESTS;
            case BACKEND_SERVICE_BUSY:
                return HttpStatus.SERVICE_UNAVAILABLE;
            case SERVICE_INTERRUPTED:
                return HttpStatus.SERVICE_UNAVAILABLE;
            case TEXT_LENGTH_LIMIT_EXCEEDED:
                return HttpStatus.PAYLOAD_TOO_LARGE;
            case INVALID_TEXT:
                return HttpStatus.BAD_REQUEST;
            case PROCESSING_TIMEOUT:
                return HttpStatus.SERVICE_UNAVAILABLE;
            case PROCESSING_ERROR:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            case AUDIO_ACQUISITION_TIMEOUT:
                return HttpStatus.GATEWAY_TIMEOUT;
            case BACKEND_LINK_ERROR:
                return HttpStatus.BAD_GATEWAY;
            case VOICE_STYLE_NOT_EXIST:
                return HttpStatus.NOT_FOUND;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    @Override
    public String getDescription() {
        return "火山协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return HuoShanProperty.class;
    }
    public enum HuoShanResponseCodeEnum {
        OK(3000, "请求正确"),
        INVALID_REQUEST(3001, "无效的请求"),
        CONCURRENT_LIMIT_EXCEEDED(3003, "并发超限"),
        BACKEND_SERVICE_BUSY(3005, "后端服务忙"),
        SERVICE_INTERRUPTED(3006, "服务中断"),
        TEXT_LENGTH_LIMIT_EXCEEDED(3010, "文本长度超限"),
        INVALID_TEXT(3011, "无效文本"),
        PROCESSING_TIMEOUT(3030, "处理超时"),
        PROCESSING_ERROR(3031, "处理错误"),
        AUDIO_ACQUISITION_TIMEOUT(3032, "等待获取音频超时"),
        BACKEND_LINK_ERROR(3040, "后端链路连接错误"),
        VOICE_STYLE_NOT_EXIST(3050, "音色不存在"),
        OTHER_ERROR(3060, "未知错误");

        public final Integer code;
        public final String message;

        HuoShanResponseCodeEnum(Integer code, String message) {
            this.code = code;
            this.message = message;
        }

        public static HuoShanResponseCodeEnum getByCode(Integer code) {
            for (HuoShanResponseCodeEnum value : HuoShanResponseCodeEnum.values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            return OTHER_ERROR;
        }
    }
}
