package com.ke.bella.openapi.endpoints;

import static com.ke.bella.openapi.common.AudioFormat.getContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ke.bella.openapi.protocol.realtime.RealTimeAdaptor;
import com.ke.bella.openapi.protocol.realtime.RealTimeHandler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.StreamByteSender;
import com.ke.bella.openapi.protocol.asr.flash.FlashAsrResponse;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionRequest.AudioTranscriptionReq;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionRequest.AudioTranscriptionResultReq;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionResponse.AudioTranscriptionResp;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionResponse.AudioTranscriptionResultResp;
import com.ke.bella.openapi.protocol.asr.flash.FlashAsrAdaptor;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.tts.TtsAdaptor;
import com.ke.bella.openapi.protocol.tts.TtsProperty;
import com.ke.bella.openapi.protocol.tts.TtsRequest;
import com.ke.bella.openapi.service.JobQueueService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

@EndpointAPI
@RestController
@RequestMapping("/v1/audio")
@Tag(name = "audio能力点")
@Slf4j
public class AudioController {
    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointLogger logger;
    @Autowired
    private JobQueueService jobQueueService;

    /**
     * 实时语音识别WebSocket接口
     */
    @RequestMapping({"/realtime", "/asr/stream"})
    public void asrStream(@RequestParam(required = false) String model,
            HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if(!"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String endpoint = EndpointContext.getRequest().getRequestURI();
        EndpointContext.setEndpointData(endpoint, model, null);
        EndpointProcessData processData = EndpointContext.getProcessData();

        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        EndpointContext.setEndpointData(channel);

        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }

        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();

        RealTimeAdaptor<AsrProperty> adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, RealTimeAdaptor.class);

        AsrProperty property = JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());

        RealTimeHandler webSocketHandler = new RealTimeHandler(url, property, processData, logger, adaptor);

        WebSocketHttpRequestHandler requestHandler = new WebSocketHttpRequestHandler(webSocketHandler);
        requestHandler.handleRequest(request, response);
    }

    @PostMapping("/speech")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void speech(@RequestBody TtsRequest request, HttpServletRequest httpRequest, HttpServletResponse response) throws IOException {
        String ttsEndpoint = EndpointContext.getRequest().getRequestURI();
        String ttsModel = request.getModel();
        EndpointContext.setEndpointData(ttsEndpoint, ttsModel, request);
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB ttsChannel = router.route(ttsEndpoint, ttsModel, EndpointContext.getApikey(), processData.isMock());
        EndpointContext.setEndpointData(ttsChannel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), ttsModel);
        }
        String ttsProtocol = processData.getProtocol();
        String ttsUrl = processData.getForwardUrl();
        String ttsChannelInfo = ttsChannel.getChannelInfo();

        TtsAdaptor ttsAdaptor = adaptorManager.getProtocolAdaptor(ttsEndpoint, ttsProtocol, TtsAdaptor.class);
        TtsProperty ttsProperty = (TtsProperty) JacksonUtils.deserialize(ttsChannelInfo, ttsAdaptor.getPropertyClass());
        if(StringUtils.isBlank(request.getResponseFormat())) {
            request.setResponseFormat(ttsProperty.getDefaultContentType());
        }
        EndpointContext.setEncodingType(ttsProperty.getEncodingType());
        if(request.isStream()) {
            AsyncContext asyncContext = httpRequest.startAsync();
            asyncContext.setTimeout(1200000);
            try {
                response.setContentType(getContentType(request.getResponseFormat()));
                OutputStream outputStream = response.getOutputStream();
                ttsAdaptor.streamTts(request, ttsUrl, ttsProperty,
                        ttsAdaptor.buildCallback(request, new StreamByteSender(asyncContext, outputStream), processData, logger));
                return;
            } catch (Exception e) {
                asyncContext.complete();
                throw e;
            }
        }
        byte[] data = ttsAdaptor.tts(request, ttsUrl, ttsProperty);
        response.setContentType(getContentType(request.getResponseFormat()));
        response.getOutputStream().write(data);
    }


    @PostMapping("/transcriptions/file")
    public AudioTranscriptionResp transcribeAudio(@RequestBody AudioTranscriptionReq audioTranscriptionReq) {
        validateRequestParams(audioTranscriptionReq);
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String taskId = jobQueueService
                .putTask(audioTranscriptionReq, endpoint, audioTranscriptionReq.getModel(), EndpointContext.getProcessData().getApikey())
                .getTaskId();
        return AudioTranscriptionResp.builder()
                .taskId(taskId)
                .build();
    }

    @PostMapping("/transcriptions/file/result")
    public AudioTranscriptionResultResp getTranscriptionResult(@RequestBody AudioTranscriptionResultReq audioTranscriptionResultReq) {
        List<Object> data = jobQueueService.getTaskResult(audioTranscriptionResultReq.getTaskId(), EndpointContext.getProcessData().getApikey()).getData();
        return AudioTranscriptionResultResp.builder()
                .data(data)
                .build();
    }

    private void validateRequestParams(AudioTranscriptionReq audioTranscriptionReq) {
        if (audioTranscriptionReq.getModel() == null || audioTranscriptionReq.getModel().isEmpty()) {
            throw new IllegalArgumentException("Model is required");
        }
        if (audioTranscriptionReq.getCallbackUrl() == null || audioTranscriptionReq.getCallbackUrl().isEmpty()) {
            throw new IllegalArgumentException("Callback url is required");
        }
        if (audioTranscriptionReq.getUrl() == null || audioTranscriptionReq.getUrl().isEmpty()) {
            throw new IllegalArgumentException("Url is required");
        }
        if (audioTranscriptionReq.getUser() == null || audioTranscriptionReq.getUser().isEmpty()) {
            throw new IllegalArgumentException("User is required");
        }
    }

    @PostMapping("/asr/flash")
    public FlashAsrResponse flashAsr(@RequestHeader(value = "format", defaultValue = "wav") String format,
            @RequestHeader(value = "sample_rate",defaultValue = "16000") int sampleRate,
            @RequestHeader(value = "max_sentence_silence", defaultValue = "3000") int maxSentenceSilence,
            @RequestHeader(value = "model", required = false) String model, InputStream inputStream) throws IOException {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        AsrRequest request = AsrRequest.builder()
                .model(model)
                .format(format)
                .maxSentenceSilence(maxSentenceSilence)
                .sampleRate(sampleRate)
                .content(StreamUtils.copyToByteArray(inputStream))
                .build();
        EndpointContext.setEndpointData(endpoint, model, request);
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        EndpointContext.setEndpointData(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        FlashAsrAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, FlashAsrAdaptor.class);
        AsrProperty property = (AsrProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        return adaptor.asr(request, url, property, processData);
    }

}
