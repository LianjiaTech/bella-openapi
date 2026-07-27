package com.ke.bella.openapi.protocol.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.VertexConverter;
import com.ke.bella.openapi.protocol.completion.VertexProperty;
import com.ke.bella.openapi.protocol.completion.gemini.GeminiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.ListSearchParameters;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.queue.Task;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component("VertexBatchAdaptor")
public class VertexBatchAdaptor implements BatchAdaptor<VertexBatchProperty> {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final MediaType JSONL = MediaType.parse("application/jsonl");
    private static final String DEFAULT_DISPLAY_NAME_PREFIX = "bella_vertex_batch_";
    private static final String DEFAULT_GCS_ENDPOINT = "https://storage.googleapis.com";
    private static final String TASK_ID_LABEL = "bella_task_id";

    @Override
    public String uploadTasks(List<Task> tasks, VertexBatchProperty property) {
        String content = tasks.stream()
                .map(task -> {
                    Map<String, Object> jsonMap = new HashMap<>();
                    jsonMap.put("request", vertexRequestBody(task, property));
                    return JacksonUtils.serialize(jsonMap);
                })
                .collect(Collectors.joining("\n"));
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        String objectName = normalizePrefix(property.getInputPrefix()) + displayName(property) + ".jsonl";
        Request request = requestBuilder(property.getAuth())
                .url(gcsUploadUrl(property, objectName))
                .post(RequestBody.create(JSONL, bytes))
                .header("Content-Type", "application/jsonl")
                .build();

        try (Response response = HttpUtils.httpRequest(request)) {
            if(!response.isSuccessful()) {
                throw new BatchRetriableException(String.format(
                        "Failed to upload Vertex batch input to GCS: HTTP %s, bucket: %s, object: %s, body: %s",
                        response.code(), property.getGcsBucket(), objectName, responseBody(response)));
            }
            String inputUri = gcsUri(property.getGcsBucket(), objectName);
            log.info("Successfully uploaded Vertex batch input to GCS, inputUri: {}, tasks: {}", inputUri, tasks.size());
            return inputUri;
        } catch (BatchRetriableException e) {
            throw e;
        } catch (Exception e) {
            throw new BatchRetriableException("Failed to upload Vertex batch input to GCS: " + objectName, e);
        }
    }

    @Override
    public List<String> downloadTasks(String fileId, VertexBatchProperty property) {
        GcsUri outputUri = parseGcsUri(fileId, property.getGcsBucket());
        List<String> objectNames = listGcsObjects(outputUri.getBucket(), outputUri.getObject(), property).stream()
                .filter(name -> StringUtils.isNotBlank(name) && name.toLowerCase(Locale.ROOT).endsWith(".jsonl"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        List<String> lines = new ArrayList<>();
        for (String objectName : objectNames) {
            String content = downloadGcsObject(outputUri.getBucket(), objectName, property);
            lines.addAll(Arrays.stream(content.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .map(this::toWorkerResult)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList()));
        }
        log.info("Downloaded Vertex batch results from GCS, outputUri: {}, objects: {}, lines: {}",
                fileId, objectNames.size(), lines.size());
        return lines;
    }

    @Override
    public Batch createBatch(BatchRequest request, String url, VertexBatchProperty property) {
        String inputUri = request.getInputFileId();
        String runId = runId(inputUri);
        String outputUriPrefix = gcsUri(property.getGcsBucket(), normalizePrefix(property.getOutputPrefix()) + runId + "/");

        Map<String, Object> payload = new HashMap<>();
        payload.put("displayName", runId);
        payload.put("model", property.getModel());
        payload.put("inputConfig", gcsInputConfig(inputUri));
        payload.put("outputConfig", gcsOutputConfig(outputUriPrefix));

        Map<String, Object> response = vertexRequest(requestBuilder(property.getAuth())
                .url(batchJobsUrl(url))
                .post(RequestBody.create(JSON, JacksonUtils.toByte(payload)))
                .header("Content-Type", "application/json")
                .build(), "create Vertex batch prediction job");

        String name = MapUtils.getString(response, "name");
        String batchId = lastPathSegment(name);
        if(StringUtils.isBlank(batchId)) {
            throw new RuntimeException("Failed to create Vertex batch prediction job: no job name returned, response: "
                    + JacksonUtils.serialize(response));
        }
        String outputFileId = outputUri(response, outputUriPrefix);
        log.info("Successfully created Vertex batch prediction job, batchId: {}, inputUri: {}, outputUri: {}",
                batchId, inputUri, outputFileId);
        return toBatch(batchId, convertStatus(MapUtils.getString(response, "state"), "validating"), outputFileId);
    }

    @Override
    public Batch retrieveBatch(String batchId, String url, VertexBatchProperty property) {
        Map<String, Object> response = vertexRequest(requestBuilder(property.getAuth())
                .url(batchJobsUrl(url) + "/" + batchId)
                .get()
                .build(), "retrieve Vertex batch prediction job: " + batchId);
        String outputUri = outputUri(response, null);
        log.info("Retrieved Vertex batch prediction job, batchId: {}, state: {}, outputUri: {}",
                batchId, MapUtils.getString(response, "state"), outputUri);
        return toBatch(batchId, convertStatus(MapUtils.getString(response, "state"), "in_progress"), outputUri);
    }

    @Override
    public OpenAiResponse<Batch> listBatches(ListSearchParameters request, String url, VertexBatchProperty property) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(batchJobsUrl(url)).newBuilder();
        if(request.getLimit() != null) {
            urlBuilder.addQueryParameter("pageSize", String.valueOf(request.getLimit()));
        }
        if(StringUtils.isNotBlank(request.getAfter())) {
            urlBuilder.addQueryParameter("pageToken", request.getAfter());
        }
        Map<String, Object> response = vertexRequest(requestBuilder(property.getAuth())
                .url(urlBuilder.build())
                .get()
                .build(), "list Vertex batch prediction jobs");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> jobs = (List<Map<String, Object>>) response.getOrDefault("batchPredictionJobs", Collections.emptyList());
        List<Batch> batches = jobs.stream()
                .map(job -> toBatch(lastPathSegment(MapUtils.getString(job, "name")),
                        convertStatus(MapUtils.getString(job, "state"), "in_progress"), outputUri(job, null)))
                .collect(Collectors.toList());
        OpenAiResponse<Batch> result = new OpenAiResponse<>();
        result.setObject("list");
        result.setData(batches);
        result.setHasMore(StringUtils.isNotBlank(MapUtils.getString(response, "nextPageToken")));
        result.setLastId(MapUtils.getString(response, "nextPageToken"));
        return result;
    }

    @Override
    public Batch cancelBatch(String batchId, String url, VertexBatchProperty property) {
        vertexRequest(requestBuilder(property.getAuth())
                .url(batchJobsUrl(url) + "/" + batchId + ":cancel")
                .post(RequestBody.create(JSON, new byte[0]))
                .build(), "cancel Vertex batch prediction job: " + batchId);
        return toBatch(batchId, "cancelling", null);
    }

    @Override
    public String getDescription() {
        return "Vertex AI BatchPredictionJob + GCS协议";
    }

    @Override
    public Class<VertexBatchProperty> getPropertyClass() {
        return VertexBatchProperty.class;
    }

    private Map<String, Object> gcsInputConfig(String inputUri) {
        Map<String, Object> source = new HashMap<>();
        source.put("uris", Collections.singletonList(inputUri));
        Map<String, Object> config = new HashMap<>();
        config.put("instancesFormat", "jsonl");
        config.put("gcsSource", source);
        return config;
    }

    private Map<String, Object> gcsOutputConfig(String outputUriPrefix) {
        Map<String, Object> destination = new HashMap<>();
        destination.put("outputUriPrefix", outputUriPrefix);
        Map<String, Object> config = new HashMap<>();
        config.put("predictionsFormat", "jsonl");
        config.put("gcsDestination", destination);
        return config;
    }

    private Map<String, Object> vertexRequestBody(Task task, VertexBatchProperty property) {
        CompletionRequest completionRequest = JacksonUtils.deserialize(JacksonUtils.serialize(task.getData()), CompletionRequest.class);
        if(completionRequest == null) {
            throw new IllegalArgumentException("Vertex batch task data is invalid, taskId: " + task.getTaskId());
        }
        VertexProperty vertexProperty = new VertexProperty();
        vertexProperty.setSupportSystemInstruction(property.isSupportSystemInstruction());
        vertexProperty.setSupportThinkConfig(property.isSupportThinkConfig());
        Map<String, Object> request = JacksonUtils.toMap(VertexConverter.convertToVertexRequest(completionRequest, vertexProperty));
        Map<String, Object> labels = new HashMap<>();
        labels.put(TASK_ID_LABEL, task.getTaskId());
        request.put("labels", labels);
        return request;
    }

    private String displayName(VertexBatchProperty property) {
        String prefix = StringUtils.defaultIfBlank(property.getDisplayNamePrefix(), DEFAULT_DISPLAY_NAME_PREFIX);
        return prefix + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String runId(String inputUri) {
        String object = parseGcsUri(inputUri, null).getObject();
        String name = StringUtils.substringAfterLast(object, "/");
        return StringUtils.removeEnd(name, ".jsonl");
    }

    private String batchJobsUrl(String url) {
        if(StringUtils.isBlank(url)) {
            throw new IllegalArgumentException("Vertex batch url is required");
        }
        return StringUtils.removeEnd(url, "/");
    }

    private Request.Builder requestBuilder(AuthorizationProperty auth) {
        Request.Builder builder = new Request.Builder();
        if(auth == null) {
            return builder;
        }
        if(auth.getType() == AuthorizationProperty.AuthType.GOOGLE_AUTH || auth.getType() == AuthorizationProperty.AuthType.BEARER) {
            String token = auth.getApiKey();
            if(StringUtils.isNotBlank(token)) {
                builder.header("Authorization", "Bearer " + token);
            }
            return builder;
        }
        String apiKey = auth.getApiKey();
        if(StringUtils.isNotBlank(apiKey)) {
            builder.header("x-goog-api-key", apiKey);
        }
        return builder;
    }

    private Map<String, Object> vertexRequest(Request request, String operation) {
        try (Response response = HttpUtils.httpRequest(request)) {
            String body = response.body() == null ? "" : response.body().string();
            if(!response.isSuccessful()) {
                throw new RuntimeException("Failed to " + operation + ": HTTP " + response.code() + ", body: " + body);
            }
            if(StringUtils.isBlank(body)) {
                return new HashMap<>();
            }
            Map<String, Object> map = JacksonUtils.deserialize(body, new TypeReference<Map<String, Object>>() {
            });
            return map == null ? new HashMap<>() : map;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to " + operation, e);
        }
    }

    private String responseBody(Response response) {
        try {
            return response.body() == null ? "" : response.body().string();
        } catch (Exception e) {
            return "Failed to read response body: " + e.getMessage();
        }
    }

    private String gcsUploadUrl(VertexBatchProperty property, String objectName) {
        return gcsBaseUrl(property) + "/upload/storage/v1/b/" + encode(property.getGcsBucket())
                + "/o?uploadType=media&name=" + encode(objectName);
    }

    private String gcsBaseUrl(VertexBatchProperty property) {
        return StringUtils.removeEnd(StringUtils.defaultIfBlank(property.getGcsEndpoint(), DEFAULT_GCS_ENDPOINT), "/");
    }

    private List<String> listGcsObjects(String bucket, String prefix, VertexBatchProperty property) {
        List<String> objectNames = new ArrayList<>();
        String pageToken = null;
        do {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(gcsBaseUrl(property) + "/storage/v1/b/" + encode(bucket) + "/o").newBuilder()
                    .addQueryParameter("prefix", prefix);
            if(StringUtils.isNotBlank(pageToken)) {
                urlBuilder.addQueryParameter("pageToken", pageToken);
            }
            Map<String, Object> response = vertexRequest(requestBuilder(property.getAuth())
                    .url(urlBuilder.build())
                    .get()
                    .build(), "list GCS objects");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getOrDefault("items", Collections.emptyList());
            items.stream()
                    .map(item -> MapUtils.getString(item, "name"))
                    .filter(StringUtils::isNotBlank)
                    .forEach(objectNames::add);
            pageToken = MapUtils.getString(response, "nextPageToken");
        } while (StringUtils.isNotBlank(pageToken));
        return objectNames;
    }

    private String downloadGcsObject(String bucket, String objectName, VertexBatchProperty property) {
        String url = gcsBaseUrl(property) + "/storage/v1/b/" + encode(bucket) + "/o/" + encode(objectName) + "?alt=media";
        try (Response response = HttpUtils.httpRequest(requestBuilder(property.getAuth())
                .url(url)
                .get()
                .build())) {
            String body = response.body() == null ? "" : response.body().string();
            if(!response.isSuccessful()) {
                throw new RuntimeException("Failed to download GCS object: HTTP " + response.code()
                        + ", bucket: " + bucket + ", object: " + objectName + ", body: " + body);
            }
            return body;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to download GCS object: " + objectName, e);
        }
    }

    private String toWorkerResult(String line) {
        Map<String, Object> row = JacksonUtils.deserialize(line, new TypeReference<Map<String, Object>>() {
        });
        if(row == null) {
            log.warn("Skip invalid Vertex batch result line: {}", line);
            return null;
        }
        String customId = customId(row);
        if(StringUtils.isBlank(customId)) {
            log.warn("Skip Vertex batch result line without custom_id: {}", line);
            return null;
        }

        String status = MapUtils.getString(row, "status");
        Object responseObj = firstPresent(row, "response", "prediction");
        Object error = row.get("error");
        Map<String, Object> response;
        if(StringUtils.isNotBlank(status)) {
            response = new HashMap<>();
            response.put("status_code", 500);
            response.put("body", status);
            response.put("error", status);
        } else if(error != null) {
            response = new HashMap<>();
            response.put("status_code", 500);
            response.put("body", error);
            response.put("error", error);
        } else if(responseObj instanceof Map && ((Map<?, ?>) responseObj).containsKey("status_code")) {
            response = new HashMap<>((Map<String, Object>) responseObj);
        } else {
            response = new HashMap<>();
            response.put("status_code", 200);
            response.put("body", openAiBody(responseObj));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("custom_id", customId);
        result.put("response", response);
        return JacksonUtils.serialize(result);
    }

    @SuppressWarnings("unchecked")
    private Object openAiBody(Object responseObj) {
        if(!(responseObj instanceof Map)) {
            return responseObj;
        }
        Map<String, Object> responseMap = (Map<String, Object>) responseObj;
        if(responseMap.containsKey("choices") || responseMap.containsKey("object")) {
            return responseMap;
        }
        GeminiResponse geminiResponse = JacksonUtils.deserialize(JacksonUtils.serialize(responseMap), GeminiResponse.class);
        if(geminiResponse == null) {
            return responseMap;
        }
        return JacksonUtils.toMap(VertexConverter.convertToOpenAIResponse(geminiResponse));
    }

    @SuppressWarnings("unchecked")
    private String customId(Map<String, Object> row) {
        String customId = MapUtils.getString(row, "custom_id", MapUtils.getString(row, "customId", MapUtils.getString(row, "id")));
        if(StringUtils.isNotBlank(customId)) {
            return customId;
        }
        Object instance = firstPresent(row, "instance", "request");
        if(instance instanceof Map) {
            Map<String, Object> instanceMap = (Map<String, Object>) instance;
            Map<String, Object> labels = (Map<String, Object>) instanceMap.get("labels");
            String taskId = labels == null ? null : MapUtils.getString(labels, TASK_ID_LABEL);
            if(StringUtils.isNotBlank(taskId)) {
                return taskId;
            }
            return MapUtils.getString(instanceMap, "custom_id",
                    MapUtils.getString(instanceMap, "customId", MapUtils.getString(instanceMap, "id")));
        }
        return null;
    }

    private Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if(row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String outputUri(Map<String, Object> response, String defaultUri) {
        Map<String, Object> outputInfo = (Map<String, Object>) response.get("outputInfo");
        String uri = outputInfo == null ? null : MapUtils.getString(outputInfo, "gcsOutputDirectory");
        if(StringUtils.isBlank(uri)) {
            Map<String, Object> outputConfig = (Map<String, Object>) response.get("outputConfig");
            Map<String, Object> destination = outputConfig == null ? null : (Map<String, Object>) outputConfig.get("gcsDestination");
            uri = destination == null ? null : MapUtils.getString(destination, "outputUriPrefix");
        }
        return StringUtils.defaultIfBlank(uri, defaultUri);
    }

    private String convertStatus(String state, String defaultStatus) {
        if(StringUtils.isBlank(state)) {
            return defaultStatus;
        }
        switch (state) {
        case "JOB_STATE_PENDING":
        case "JOB_STATE_QUEUED":
            return "validating";
        case "JOB_STATE_RUNNING":
        case "JOB_STATE_UPDATING":
            return "in_progress";
        case "JOB_STATE_CANCELLING":
            return "cancelling";
        case "JOB_STATE_SUCCEEDED":
            return "completed";
        case "JOB_STATE_FAILED":
            return "failed";
        case "JOB_STATE_CANCELLED":
            return "cancelled";
        case "JOB_STATE_EXPIRED":
            return "expired";
        default:
            throw new RuntimeException("Unsupported Vertex batch job state: " + state);
        }
    }

    private Batch toBatch(String id, String status, String outputFileId) {
        Map<String, Object> batch = new HashMap<>();
        batch.put("id", id);
        batch.put("status", status);
        batch.put("outputFileId", outputFileId);
        batch.put("output_file_id", outputFileId);
        return JacksonUtils.deserialize(JacksonUtils.serialize(batch), Batch.class);
    }

    private GcsUri parseGcsUri(String uri, String defaultBucket) {
        if(StringUtils.isBlank(uri)) {
            throw new IllegalArgumentException("GCS uri is required");
        }
        if(uri.startsWith("gs://")) {
            URI parsed = URI.create(uri);
            return new GcsUri(parsed.getHost(), StringUtils.removeStart(parsed.getPath(), "/"));
        }
        if(StringUtils.isBlank(defaultBucket)) {
            throw new IllegalArgumentException("GCS bucket is required for object path: " + uri);
        }
        return new GcsUri(defaultBucket, StringUtils.removeStart(uri, "/"));
    }

    private String gcsUri(String bucket, String objectName) {
        if(StringUtils.isBlank(bucket)) {
            throw new IllegalArgumentException("Vertex batch property gcsBucket is required");
        }
        return "gs://" + bucket + "/" + StringUtils.removeStart(objectName, "/");
    }

    private String normalizePrefix(String prefix) {
        if(StringUtils.isBlank(prefix)) {
            return "";
        }
        return StringUtils.appendIfMissing(StringUtils.removeStart(prefix, "/"), "/");
    }

    private String lastPathSegment(String name) {
        return StringUtils.substringAfterLast(StringUtils.defaultString(name), "/");
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(StringUtils.defaultString(value), "UTF-8")
                    .replace("+", "%20")
                    .replace("%2F", "%2F");
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode value", e);
        }
    }

    private static class GcsUri {
        private final String bucket;
        private final String object;

        private GcsUri(String bucket, String object) {
            this.bucket = bucket;
            this.object = object;
        }

        private String getBucket() {
            return bucket;
        }

        private String getObject() {
            return object;
        }
    }
}
