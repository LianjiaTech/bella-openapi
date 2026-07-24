package com.ke.bella.openapi.protocol.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.ListSearchParameters;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.queue.Task;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.model.object.GetObjectV2Input;
import com.volcengine.tos.model.object.GetObjectV2Output;
import com.volcengine.tos.model.object.ListObjectsType2Input;
import com.volcengine.tos.model.object.ListObjectsType2Output;
import com.volcengine.tos.model.object.ListedObjectV2;
import com.volcengine.tos.model.object.PutObjectInput;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component("HuoshanJobBatchAdaptor")
public class HuoshanJobBatchAdaptor implements BatchAdaptor<HuoshanJobProperty> {

    private static final String SERVICE = "ark";
    private static final String VERSION = "2024-01-01";
    private static final String CREATE_BATCH_INFERENCE_JOB = "CreateBatchInferenceJob";
    private static final String GET_BATCH_INFERENCE_JOB = "GetBatchInferenceJob";
    private static final String STOP_BATCH_INFERENCE_JOB = "StopBatchInferenceJob";
    private static final String DEFAULT_COMPLETION_WINDOW = "1d";

    @Override
    public String uploadTasks(List<Task> tasks, HuoshanJobProperty property) {
        String jsonlContent = tasks.stream()
                .map(task -> {
                    Map<String, Object> jsonMap = new HashMap<>();
                    jsonMap.put("custom_id", task.getTaskId());
                    jsonMap.put("body", task.getData());
                    return JacksonUtils.serialize(jsonMap);
                })
                .collect(Collectors.joining("\n"));

        String inputKey = normalizePrefix(property.getInputPrefix()) + "batch_input_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8) + ".jsonl";
        byte[] bytes = jsonlContent.getBytes(StandardCharsets.UTF_8);

        try (TOSV2 tos = tosClient(property)) {
            PutObjectInput input = PutObjectInput.builder()
                    .bucket(property.getTosBucket())
                    .key(inputKey)
                    .content(new ByteArrayInputStream(bytes))
                    .contentLength(bytes.length)
                    .build();
            tos.putObject(input);
            log.info("Successfully uploaded Huoshan batch input to TOS, bucket: {}, key: {}, tasks: {}",
                    property.getTosBucket(), inputKey, tasks.size());
            return inputKey;
        } catch (Exception e) {
            throw new BatchRetriableException("Failed to upload Huoshan batch input to TOS: " + inputKey, e);
        }
    }

    @Override
    public List<String> downloadTasks(String fileId, HuoshanJobProperty property) {
        String resultPrefix = normalizePrefix(fileId);
        List<String> rawLines = new ArrayList<>();

        try (TOSV2 tos = tosClient(property)) {
            List<String> resultKeys = listResultKeys(tos, property.getTosBucket(), resultPrefix);
            resultKeys.forEach(key -> rawLines.addAll(downloadResultObject(tos, property.getTosBucket(), key)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to download Huoshan batch result from TOS, bucket: "
                    + property.getTosBucket() + ", prefix: " + resultPrefix, e);
        }

        List<String> lines = rawLines.stream()
                .map(this::toWorkerResult)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        log.info("Downloaded Huoshan batch result from TOS, prefix: {}, valid lines: {}", resultPrefix, lines.size());
        return lines;
    }

    @SuppressWarnings("unchecked")
    private String toWorkerResult(String line) {
        Map<String, Object> row = JacksonUtils.deserialize(line, new TypeReference<Map<String, Object>>() {
        });
        if(row == null) {
            log.warn("Skip invalid Huoshan batch result line: {}", line);
            return null;
        }
        String customId = MapUtils.getString(row, "custom_id");
        if(StringUtils.isBlank(customId)) {
            customId = MapUtils.getString(row, "customId", MapUtils.getString(row, "id"));
        }
        if(StringUtils.isBlank(customId)) {
            log.warn("Skip Huoshan batch result line without custom_id: {}", line);
            return null;
        }

        Object responseObj = row.get("response");
        Object error = row.get("error");
        boolean providerError = error != null || (row.containsKey("response") && responseObj == null);

        Map<String, Object> response;
        if(providerError) {
            response = errorResponse(row, error);
        } else if(responseObj instanceof Map) {
            response = new HashMap<>((Map<String, Object>) responseObj);
            response.putIfAbsent("status_code", 200);
        } else {
            response = new HashMap<>();
            response.put("status_code", statusCode(row, false));
            response.put("body", row.containsKey("body") ? row.get("body") : responseObj);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("custom_id", customId);
        result.put("response", response);
        return JacksonUtils.serialize(result);
    }

    private Map<String, Object> errorResponse(Map<String, Object> row, Object error) {
        Map<String, Object> response = new HashMap<>();
        response.put("status_code", statusCode(row, true));
        response.put("body", error == null ? row : error);
        if(error != null) {
            response.put("error", error);
        }
        return response;
    }

    private int statusCode(Map<String, Object> row, boolean providerError) {
        Object statusCode = row.get("status_code");
        if(statusCode instanceof Number) {
            return normalizeStatusCode(((Number) statusCode).intValue(), providerError);
        }
        Object response = row.get("response");
        if(response instanceof Map) {
            Object nestedStatusCode = ((Map<?, ?>) response).get("status_code");
            if(nestedStatusCode instanceof Number) {
                return normalizeStatusCode(((Number) nestedStatusCode).intValue(), providerError);
            }
        }
        return providerError ? 500 : 200;
    }

    private int normalizeStatusCode(int statusCode, boolean providerError) {
        return providerError && statusCode >= 200 && statusCode < 300 ? 500 : statusCode;
    }

    private String outputFileId(HuoshanJobProperty property, String batchId) {
        return normalizePrefix(property.getOutputPrefix()) + batchId + "/";
    }

    private String normalizePrefix(String prefix) {
        if(StringUtils.isBlank(prefix)) {
            return "";
        }
        return StringUtils.appendIfMissing(StringUtils.removeStart(prefix, "/"), "/");
    }

    private List<String> listResultKeys(TOSV2 tos, String bucket, String prefix) {
        List<String> resultKeys = new ArrayList<>();
        String continuationToken = null;
        do {
            ListObjectsType2Input input = ListObjectsType2Input.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build();
            ListObjectsType2Output output = tos.listObjectsType2(input);
            resultKeys.addAll(output.getContents().stream()
                    .map(ListedObjectV2::getKey)
                    .filter(key -> StringUtils.isNotBlank(key) && key.toLowerCase(Locale.ROOT).endsWith(".jsonl"))
                    .collect(Collectors.toList()));
            continuationToken = output.isTruncated() ? output.getNextContinuationToken() : null;
        } while (StringUtils.isNotBlank(continuationToken));
        resultKeys.sort(Comparator.naturalOrder());
        return resultKeys;
    }

    @Override
    public Batch createBatch(BatchRequest request, String url, HuoshanJobProperty property) {
        String outputDir = normalizePrefix(property.getOutputPrefix());

        Map<String, Object> payload = new HashMap<>();
        payload.put("Name", "bella-batch-" + System.currentTimeMillis());
        payload.put("InputFileTosLocation", ImmutableMap.of(
                "BucketName", property.getTosBucket(),
                "ObjectKey", request.getInputFileId()));
        payload.put("OutputDirTosLocation", ImmutableMap.of(
                "BucketName", property.getTosBucket(),
                "ObjectKey", outputDir));
        payload.put("ModelReference", ImmutableMap.of(
                "FoundationModel", ImmutableMap.of(
                        "Name", property.getModel(),
                        "ModelVersion", property.getModelVersion())));
        payload.put("CompletionWindow", StringUtils.defaultIfBlank(request.getCompletionWindow(), DEFAULT_COMPLETION_WINDOW));
        if(StringUtils.isNotBlank(property.getProjectName())) {
            payload.put("ProjectName", property.getProjectName());
        }

        Map<String, Object> response = openApiRequest(CREATE_BATCH_INFERENCE_JOB, payload, url, property);
        Map<String, Object> result = responseResult(response);
        String batchId = MapUtils.getString(result, "Id");
        if(StringUtils.isBlank(batchId)) {
            throw new RuntimeException("Failed to create Huoshan batch job: no job id returned, result: "
                    + JacksonUtils.serialize(result));
        }

        String outputFileId = outputFileId(property, batchId);
        log.info("Successfully created Huoshan batch job, batchId: {}, outputDir: {}, outputFileId: {}",
                batchId, outputDir, outputFileId);
        return toBatch(batchId, "validating", outputFileId);
    }

    @Override
    public Batch retrieveBatch(String batchId, String url, HuoshanJobProperty property) {
        Map<String, Object> response = openApiRequest(GET_BATCH_INFERENCE_JOB, Collections.singletonMap("Id", batchId), url, property);
        Map<String, Object> result = responseResult(response);
        String status = convertStatus(result, "in_progress");
        String outputPrefix = outputFileId(property, batchId);
        log.info("Retrieved Huoshan batch job, batchId: {}, mappedStatus: {}, outputPrefix: {}",
                batchId, status, outputPrefix);
        return toBatch(batchId, status, outputPrefix);
    }

    @Override
    public OpenAiResponse<Batch> listBatches(ListSearchParameters request, String url, HuoshanJobProperty property) {
        throw new UnsupportedOperationException("Huoshan batch job does not support OpenAI-compatible list batches");
    }

    @Override
    public Batch cancelBatch(String batchId, String url, HuoshanJobProperty property) {
        Map<String, Object> response = openApiRequest(STOP_BATCH_INFERENCE_JOB, Collections.singletonMap("Id", batchId), url, property);
        Map<String, Object> result = responseResult(response);
        String status = convertStatus(result, "cancelling");
        return toBatch(batchId, status, outputFileId(property, batchId));
    }

    @Override
    public String getDescription() {
        return "火山方舟 Batch Job API协议";
    }

    @Override
    public Class<HuoshanJobProperty> getPropertyClass() {
        return HuoshanJobProperty.class;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseResult(Map<String, Object> response) {
        return (Map<String, Object>) MapUtils.getMap(response, "Result");
    }

    private List<String> downloadResultObject(TOSV2 tos, String bucket, String key) {
        try (GetObjectV2Output output = tos.getObject(GetObjectV2Input.builder().bucket(bucket).key(key).build());
                InputStream inputStream = output.getContent()) {
            if(inputStream == null) {
                log.warn("Skip empty Huoshan batch result object, bucket: {}, key: {}", bucket, key);
                return Collections.emptyList();
            }
            String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            List<String> lines = Arrays.stream(content.split("\n"))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            log.info("Downloaded Huoshan batch result object, bucket: {}, key: {}, lines: {}", bucket, key, lines.size());
            return lines;
        } catch (Exception e) {
            throw new RuntimeException("Failed to download Huoshan batch result object: " + key, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> openApiRequest(String action, Map<String, Object> payload, String baseUrl, HuoshanJobProperty property) {
        byte[] body = JacksonUtils.toByte(payload);
        Request request = signedRequest(action, body, baseUrl, property);
        try (Response response = HttpUtils.httpRequest(request)) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if(!response.isSuccessful()) {
                throw new RuntimeException("Huoshan OpenAPI request failed, action: " + action + ", httpStatus: "
                        + response.code() + ", body: " + responseBody);
            }
            Map<String, Object> map = JacksonUtils.deserialize(responseBody, new TypeReference<Map<String, Object>>() {
            });
            if(map == null) {
                throw new RuntimeException("Huoshan OpenAPI request failed, action: " + action + ", empty response");
            }
            Map<String, Object> metadata = (Map<String, Object>) MapUtils.getMap(map, "ResponseMetadata");
            Map<String, Object> error = metadata == null ? null : (Map<String, Object>) MapUtils.getMap(metadata, "Error");
            if(error != null && !error.isEmpty()) {
                throw new RuntimeException("Huoshan OpenAPI error, action: " + action + ", error: " + JacksonUtils.serialize(error));
            }
            return map;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Huoshan OpenAPI request failed, action: " + action, e);
        }
    }

    private Request signedRequest(String action, byte[] body, String baseUrl, HuoshanJobProperty property) {
        AuthorizationProperty auth = property.getAuth();
        String region = property.getRegion();
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(StringUtils.removeEnd(baseUrl, "/")))
                .newBuilder()
                .addQueryParameter("Action", action)
                .addQueryParameter("Version", VERSION)
                .build();
        String host = url.host();
        if(url.port() != HttpUrl.defaultPort(url.scheme())) {
            host = host + ":" + url.port();
        }

        String xDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        String shortDate = xDate.substring(0, 8);
        String payloadHash = sha256Hex(body);
        String canonicalUri = StringUtils.defaultIfBlank(url.encodedPath(), "/");
        String signedHeaders = "host;x-content-sha256;x-date";

        String canonicalHeaders = String.format("host:%s\nx-content-sha256:%s\nx-date:%s\n", host, payloadHash, xDate);
        String canonicalRequest = String.join("\n",
                "POST",
                canonicalUri,
                canonicalQuery(url),
                canonicalHeaders,
                signedHeaders,
                payloadHash);
        String credentialScope = String.join("/", shortDate, region, SERVICE, "request");
        String stringToSign = String.join("\n",
                "HMAC-SHA256",
                xDate,
                credentialScope,
                sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        String signature = hex(hmac(signingKey(auth.getSecret(), shortDate, region), stringToSign));
        String authorization = String.format("HMAC-SHA256 Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                auth.getApiKey(), credentialScope, signedHeaders, signature);

        return new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), body))
                .header("Content-Type", "application/json")
                .header("Host", host)
                .header("X-Date", xDate)
                .header("X-Content-Sha256", payloadHash)
                .header("Authorization", authorization)
                .build();
    }

    private TOSV2 tosClient(HuoshanJobProperty property) {
        AuthorizationProperty auth = property.getAuth();
        return new TOSV2ClientBuilder().build(property.getRegion(), property.getTosEndpoint(), auth.getApiKey(), auth.getSecret());
    }

    private Batch toBatch(String id, String status, String outputFileId) {
        Map<String, Object> batch = new HashMap<>();
        batch.put("id", id);
        batch.put("status", status);
        batch.put("outputFileId", outputFileId);
        batch.put("output_file_id", outputFileId);
        return JacksonUtils.deserialize(JacksonUtils.serialize(batch), Batch.class);
    }

    @SuppressWarnings("unchecked")
    private String convertStatus(Map<String, Object> result, String defaultStatus) {
        Map<String, Object> huoshanStatus = (Map<String, Object>) MapUtils.getMap(result, "Status");
        String phase = huoshanStatus == null ? null : MapUtils.getString(huoshanStatus, "Phase");
        phase = StringUtils.defaultIfBlank(phase, MapUtils.getString(result, "Phase"));
        if(StringUtils.isBlank(phase)) {
            return defaultStatus;
        }
        switch (phase) {
        case "Pending":
            return "validating";
        case "Running":
        case "Cancelling":
            return "in_progress";
        case "Completed":
            return "completed";
        case "Failed":
            return "failed";
        case "Cancelled":
            return "cancelled";
        default:
            throw new RuntimeException("Unsupported Huoshan batch phase: " + phase);
        }
    }

    private String canonicalQuery(HttpUrl url) {
        List<String> names = new ArrayList<>(url.queryParameterNames());
        names.sort(Comparator.naturalOrder());
        return names.stream()
                .flatMap(name -> url.queryParameterValues(name).stream()
                        .map(value -> encode(name) + "=" + encode(value)))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(StringUtils.defaultString(value), "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode value", e);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate SHA-256", e);
        }
    }

    private byte[] signingKey(String secret, String shortDate, String region) {
        byte[] dateKey = hmac(secret.getBytes(StandardCharsets.UTF_8), shortDate);
        byte[] regionKey = hmac(dateKey, region);
        byte[] serviceKey = hmac(regionKey, SERVICE);
        return hmac(serviceKey, "request");
    }

    private byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign Huoshan OpenAPI request", e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
