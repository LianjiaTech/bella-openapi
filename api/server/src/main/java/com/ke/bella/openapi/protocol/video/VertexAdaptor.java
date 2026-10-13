package com.ke.bella.openapi.protocol.video;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.QueueClient;
import com.theokanning.openai.queue.Task;
import com.theokanning.openai.file.File;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component("VertexVideo")
public class VertexAdaptor implements VideoAdaptor<VertexProperty> {

    @Resource
    private QueueClient queueClient;

    @Override
    public void prepareRequest(VideoCreateRequest request, String apikey) {
        Map<String, Object> extraBody = request.getExtra_body();
        if(extraBody == null) {
            return;
        }

        String taskId = (String) extraBody.get("previous_interaction_id");
        if(StringUtils.isBlank(taskId)) {
            return;
        }

        Task previousTask = queueClient.getTaskDetail(taskId, apikey);
        if(previousTask == null) {
            throw new BellaException.ChannelException(404, "Previous video task not found: " + taskId);
        }
        extraBody.put("previous_interaction_id", resolveChannelVideoId(previousTask));
    }

    @Override
    public String submitVideoTask(VideoCreateRequest request, String baseUrl, VertexProperty property, String videoId) {
        Object requestBody = buildRequest(request, property);
        Request httpRequest = buildPostRequest(baseUrl, requestBody, property);
        VertexResponse response = HttpUtils.httpRequest(httpRequest, VertexResponse.class, errorCallback);
        if(response == null || StringUtils.isBlank(response.getId())) {
            throw new BellaException.ChannelException(502, "Vertex returned empty id");
        }
        return response.getId();
    }

    @Override
    public ChannelVideoResult queryVideoTask(String channelVideoId, String baseUrl, VertexProperty property) {
        Request request = buildQueryRequest(baseUrl, channelVideoId, property);
        VertexResponse response = HttpUtils.httpRequest(request, VertexResponse.class, errorCallback);
        return VertexConverter.convert(channelVideoId, response);
    }

    @Override
    public File transferVideoToFile(String channelVideoId, String baseUrl, VertexProperty property, OpenAiService openAiService) {
        Request request = buildQueryRequest(baseUrl, channelVideoId, property);
        VertexResponse response = HttpUtils.httpRequest(request, VertexResponse.class, errorCallback);
        String videoSource = VertexConverter.resolveVideoUri(response);
        if(StringUtils.isBlank(videoSource)) {
            throw new BellaException.ChannelException(502, "Video URI/data not found in Vertex response");
        }
        try(InputStream videoStream = openVideoStream(videoSource, property)) {
            return openAiService.uploadFile("temp", videoStream, buildVideoFileName());
        } catch(IOException e) {
            log.error("[VertexAdaptor] Failed to transfer video: channelVideoId={}", channelVideoId, e);
            throw new BellaException.ChannelException(502, "Failed to transfer Vertex video: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Google Vertex Gemini Omni Flash API";
    }

    @Override
    public Class<?> getPropertyClass() {
        return VertexProperty.class;
    }

    private Map<String, Object> buildRequest(VideoCreateRequest request, VertexProperty property) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        Map<String, Object> extraBody = request.getExtra_body();
        if(extraBody != null) {
            requestBody.putAll(extraBody);
        }
        requestBody.put("model", property.getDeployName());
        requestBody.put("background", true);
        return requestBody;
    }

    @SuppressWarnings("unchecked")
    private String resolveChannelVideoId(Task task) {
        Map<String, Object> outputData = (Map<String, Object>) queueClient.getOutputData(task);
        return String.valueOf(outputData.get("channelVideoId"));
    }

    private Request buildPostRequest(String url, Object request, VertexProperty property) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request));
        return authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build();
    }

    private Request buildQueryRequest(String baseUrl, String channelVideoId, VertexProperty property) {
        return authorizationRequestBuilder(property.getAuth())
                .url(buildPollUrl(baseUrl, channelVideoId))
                .get()
                .build();
    }

    private String buildPollUrl(String baseUrl, String channelVideoId) {
        String clean = StringUtils.removeEnd(baseUrl, "/");
        return clean + "/" + channelVideoId;
    }

    private InputStream openVideoStream(String source, VertexProperty property) throws IOException {
        if(source.startsWith("data:")) {
            int comma = source.indexOf(',');
            return new ByteArrayInputStream(java.util.Base64.getDecoder().decode(source.substring(comma + 1)));
        }
        String downloadUrl = toGcsDownloadUrl(source);
        Request request = authorizationRequestBuilder(property.getAuth()).url(downloadUrl).get().build();
        Response response = HttpUtils.httpRequest(request);
        if(!response.isSuccessful() || response.body() == null) {
            response.close();
            throw new IOException("Video download failed: HTTP " + response.code());
        }
        return new ResponseInputStream(response);
    }

    private String toGcsDownloadUrl(String source) throws IOException {
        URI uri = URI.create(source);
        String bucket = uri.getHost();
        String object = StringUtils.removeStart(uri.getPath(), "/");
        return "https://storage.googleapis.com/storage/v1/b/" + bucket + "/o/"
                + URLEncoder.encode(object, "UTF-8").replace("+", "%20") + "?alt=media";
    }

    private static final class ResponseInputStream extends FilterInputStream {
        private final Response response;

        private ResponseInputStream(Response response) {
            super(response.body().byteStream());
            this.response = response;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }

    private final Callbacks.ChannelErrorCallback<VertexResponse> errorCallback = (response, httpResponse) -> {
        String message = response == null || response.getError() == null ? httpResponse.message() : response.getError().getMessage();
        if(StringUtils.isBlank(message)) {
            message = "Vertex API error";
        }
        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .httpCode(httpResponse.code())
                .code(response == null || response.getError() == null
                        ? String.valueOf(httpResponse.code()) : response.getError().getCode())
                .message(message)
                .build();
        throw new BellaException.ChannelException(httpResponse.code(), "vertex_error", message, error);
    };
}
