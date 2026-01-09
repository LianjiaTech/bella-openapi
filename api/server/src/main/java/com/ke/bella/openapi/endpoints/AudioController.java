package com.ke.bella.openapi.endpoints;

import com.ke.bella.job.queue.JobQueueClient;
import com.ke.bella.job.queue.api.entity.response.TaskResp;
import com.ke.bella.job.queue.config.JobQueueProperties;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.StreamByteSender;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionRequest.AudioTranscriptionReq;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionRequest.AudioTranscriptionResultReq;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionResponse.AudioTranscriptionResp;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionResponse.AudioTranscriptionResultResp;
import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationAdaptor;
import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationProperty;
import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationResponse;
import com.ke.bella.openapi.protocol.asr.transcription.TranscriptionsRequest;
import com.ke.bella.openapi.protocol.asr.transcription.TranscriptionsResponse;
import com.ke.bella.openapi.utils.TranscriptionsConverter;
import com.ke.bella.openapi.protocol.asr.flash.FlashAsrAdaptor;
import com.ke.bella.openapi.protocol.asr.flash.FlashAsrResponse;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.realtime.RealTimeAdaptor;
import com.ke.bella.openapi.protocol.realtime.RealTimeHandler;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingAdaptor;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingProperty;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingRequest;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingResponse;
import com.ke.bella.openapi.protocol.tts.TtsAdaptor;
import com.ke.bella.openapi.protocol.tts.TtsProperty;
import com.ke.bella.openapi.protocol.tts.TtsRequest;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ke.bella.openapi.common.AudioFormat.getContentType;

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
    private JobQueueProperties jobQueueProperties;
    @Autowired
    private EndpointDataService endpointDataService;

    /**
     * 实时语音识别WebSocket接口
     */
    @RequestMapping({ "/realtime", "/asr/stream" })
    public void asrStream(@RequestParam(required = false) String model,
            HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if(!"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String endpoint = EndpointContext.getRequest().getRequestURI();
        endpointDataService.setEndpointData(endpoint, model, null);
        EndpointProcessData processData = EndpointContext.getProcessData();

        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);

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
        endpointDataService.setEndpointData(ttsEndpoint, ttsModel, request);
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB ttsChannel = router.route(ttsEndpoint, ttsModel, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(ttsChannel);
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

    /**
     * OpenAI-compatible audio transcriptions endpoint
     */
    @PostMapping("/transcriptions")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public TranscriptionsResponse transcriptions(TranscriptionsRequest request) throws IOException {

        // 获取文件格式
        String format = TranscriptionsConverter.getAudioFormatFromFilename(request.getFile().getOriginalFilename());

        String endpoint = "/v1/audio/asr/flash"; // 使用 Flash ASR 能力点
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request.summary());
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);

        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }

        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();

        // 调用 Flash ASR
        FlashAsrAdaptor flashAsrAdaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, FlashAsrAdaptor.class);
        AsrProperty property = (AsrProperty) JacksonUtils.deserialize(channelInfo, flashAsrAdaptor.getPropertyClass());

        AsrRequest asrRequest = AsrRequest.builder()
                .model(model)
                .format(format)
                .sampleRate(16000)
                .maxSentenceSilence(3000)
                .content(StreamUtils.copyToByteArray(request.getFile().getInputStream()))
                .build();

        FlashAsrResponse flashResponse = flashAsrAdaptor.asr(asrRequest, url, property, processData);

        // 转换为 OpenAI 格式
        return TranscriptionsConverter.convertFlashAsrToOpenAI(flashResponse, request.getResponseFormat());
    }

    @PostMapping("/transcriptions/file")
    public AudioTranscriptionResp transcribeAudio(@RequestBody AudioTranscriptionReq audioTranscriptionReq) {
        validateRequestParams(audioTranscriptionReq);
        String endpoint = EndpointContext.getRequest().getRequestURI();
        JobQueueClient client = JobQueueClient.getInstance(jobQueueProperties.getUrl());
        String taskId = client
                .put(client.buildTaskPutRequest(audioTranscriptionReq, null, endpoint, audioTranscriptionReq.getModel()),
                        EndpointContext.getProcessData().getApikey(), TaskResp.TaskPutResp.class)
                .getTaskId();
        return AudioTranscriptionResp.builder()
                .taskId(taskId)
                .build();
    }

    @PostMapping("/transcriptions/file/result")
    public AudioTranscriptionResultResp getTranscriptionResult(@RequestBody AudioTranscriptionResultReq audioTranscriptionResultReq) {
        List<Object> data = getTaskResult(audioTranscriptionResultReq.getTaskId(), EndpointContext.getProcessData().getApikey()).getData();
        return AudioTranscriptionResultResp.builder()
                .data(data)
                .build();
    }

    private QueueTaskGetResultResp getTaskResult(List<String> taskIds, String apikey) {
        JobQueueClient client = JobQueueClient.getInstance(jobQueueProperties.getUrl());
        List<Object> result = new ArrayList<>();
        for (String taskId : taskIds) {
            TaskResp.DetailData data = client.getTaskDetail(taskId, apikey).getData();
            if(data == null) {
                continue;
            }
            Object outputData = data.getOutputData();
            String outputFileId = data.getOutputFileId();
            if(outputData != null) {
                result.add(outputData);
            } else if(outputFileId != null && !outputFileId.isEmpty()) {
                Map<String, String> map = new HashMap<>();
                map.put("file_id", outputFileId);
                result.add(map);
            }
        }

        return QueueTaskGetResultResp.builder()
                .data(result)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueTaskGetResultResp {

        private List<Object> data;

    }

    private void validateRequestParams(AudioTranscriptionReq audioTranscriptionReq) {
        if(audioTranscriptionReq.getModel() == null || audioTranscriptionReq.getModel().isEmpty()) {
            throw new IllegalArgumentException("Model is required");
        }
        if(audioTranscriptionReq.getCallbackUrl() == null || audioTranscriptionReq.getCallbackUrl().isEmpty()) {
            throw new IllegalArgumentException("Callback url is required");
        }
        if(audioTranscriptionReq.getUrl() == null || audioTranscriptionReq.getUrl().isEmpty()) {
            throw new IllegalArgumentException("Url is required");
        }
        if(audioTranscriptionReq.getUser() == null || audioTranscriptionReq.getUser().isEmpty()) {
            throw new IllegalArgumentException("User is required");
        }
    }

    @PostMapping("/asr/flash")
    public FlashAsrResponse flashAsr(@RequestHeader(value = "format", defaultValue = "wav") String format,
            @RequestHeader(value = "sample_rate", defaultValue = "16000") int sampleRate,
            @RequestHeader(value = "max_sentence_silence", defaultValue = "3000") int maxSentenceSilence,
            @RequestHeader(value = "model", required = false) String model,
            @RequestHeader(value = "hot_words", defaultValue = "") String hotWords,
            @RequestHeader(value = "hot_words_table_id", defaultValue = "") String hotWordsTableId,
            @RequestHeader(value = "convert_numbers", defaultValue = "false") boolean convertNumbers,
            InputStream inputStream) throws IOException {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        // 手动解码hot_words中的中文字符
        String decodedHotWords = hotWords;
        if(StringUtils.isNotBlank(hotWords)) {
            try {
                decodedHotWords = URLDecoder.decode(hotWords, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                // 解码失败时使用原始字符串
                decodedHotWords = hotWords;
            }
        }

        AsrRequest request = AsrRequest.builder()
                .model(model)
                .format(format)
                .maxSentenceSilence(maxSentenceSilence)
                .sampleRate(sampleRate)
                .hotWords(decodedHotWords)
                .hotWordsTableId(hotWordsTableId)
                .convertNumbers(convertNumbers)
                .content(StreamUtils.copyToByteArray(inputStream))
                .build();
        endpointDataService.setEndpointData(endpoint, model, request.summary());
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);
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

    @PostMapping("/speaker/embedding")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public SpeakerEmbeddingResponse speakerEmbedding(@RequestBody SpeakerEmbeddingRequest request) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request.summary());
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        SpeakerEmbeddingAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, SpeakerEmbeddingAdaptor.class);
        SpeakerEmbeddingProperty property = (SpeakerEmbeddingProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());
        return adaptor.speakerEmbedding(request, url, property);
    }

    @PostMapping("/speaker/diarization")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public SpeakerDiarizationResponse speakerDiarization(@RequestBody AudioTranscriptionReq audioTranscriptionReq) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = audioTranscriptionReq.getModel();
        endpointDataService.setEndpointData(endpoint, model, audioTranscriptionReq);
        EndpointProcessData processData = EndpointContext.getProcessData();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
        endpointDataService.setChannel(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        SpeakerDiarizationAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, SpeakerDiarizationAdaptor.class);
        SpeakerDiarizationProperty property = (SpeakerDiarizationProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        return adaptor.speakerDiarization(audioTranscriptionReq, url, property);
    }

}
