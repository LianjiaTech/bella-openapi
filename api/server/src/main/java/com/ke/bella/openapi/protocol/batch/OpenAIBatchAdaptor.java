package com.ke.bella.openapi.protocol.batch;

import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.ListSearchParameters;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.queue.Task;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component("OpenAiBatchAdaptor")
public class OpenAIBatchAdaptor implements BatchAdaptor<OpenAiProperty> {

    @Override
    public String uploadTasks(List<Task> tasks, OpenAiProperty property) {
        String jsonlContent = tasks.stream()
                .map(task -> {
                    Map<String, Object> jsonMap = new HashMap<>();
                    jsonMap.put("custom_id", task.getTaskId());
                    jsonMap.put("method", "POST");
                    jsonMap.put("url", task.getEndpoint());
                    jsonMap.put("body", task.getData());
                    return JacksonUtils.serialize(jsonMap);
                })
                .collect(Collectors.joining("\n"));

        RequestBody fileBody = RequestBody.create(
                MediaType.parse("application/jsonl"),
                jsonlContent.getBytes(StandardCharsets.UTF_8));

        String fileName = "batch_input_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8) + ".jsonl";

        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .addFormDataPart("purpose", "batch")
                .build();

        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(property.getFileServiceUrl())
                .post(multipartBody)
                .build();

        try {
            FileUploadResponse response = HttpUtils.httpRequest(httpRequest, FileUploadResponse.class);

            if(response == null || response.getId() == null) {
                throw new RuntimeException("Failed to upload batch file: no file ID returned");
            }

            String fileId = response.getId();
            log.info("Successfully uploaded batch file, file_id: {}", fileId);
            return fileId;
        } catch (Exception e) {
            log.error("Failed to upload batch file", e);
            throw new RuntimeException("Failed to upload batch file", e);
        }
    }

    @Override
    public List<String> downloadTasks(String fileId, OpenAiProperty property) {
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(property.getFileServiceUrl() + "/" + fileId + "/content")
                .get()
                .build();

        try {
            byte[] bytes = HttpUtils.doHttpRequest(httpRequest);
            String content = new String(bytes, StandardCharsets.UTF_8);
            List<String> results = Arrays.stream(content.split("\n"))
                    .filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.toList());
            log.info("Downloaded batch result file, file_id: {}, lines: {}", fileId, results.size());
            return results;
        } catch (Exception e) {
            log.error("Failed to download batch result file: {}", fileId, e);
            throw new RuntimeException("Failed to download batch result file: " + fileId, e);
        }
    }

    @Override
    public Batch createBatch(BatchRequest request, String url, OpenAiProperty property) {
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json"),
                JacksonUtils.toByte(request));
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(requestBody)
                .build();

        try {
            Batch response = HttpUtils.httpRequest(httpRequest, Batch.class);
            if(response == null) {
                throw new RuntimeException("Failed to create batch: no response returned");
            }
            log.info("Successfully created batch, batch_id: {}, status: {}", response.getId(), response.getStatus());
            return response;
        } catch (Exception e) {
            log.error("Failed to create batch", e);
            throw new RuntimeException("Failed to create batch", e);
        }
    }

    @Override
    public Batch retrieveBatch(String batchId, String url, OpenAiProperty property) {
        String retrieveUrl = url + "/" + batchId;
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(retrieveUrl)
                .get()
                .build();

        try {
            Batch response = HttpUtils.httpRequest(httpRequest, Batch.class);
            if(response == null) {
                throw new RuntimeException("Failed to retrieve batch: no response returned");
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to retrieve batch: {}", batchId, e);
            throw new RuntimeException("Failed to retrieve batch: " + batchId, e);
        }
    }

    @Override
    public OpenAiResponse<Batch> listBatches(ListSearchParameters request, String url, OpenAiProperty property) {
        StringBuilder urlBuilder = new StringBuilder(url);
        boolean hasParam = false;
        if(request.getAfter() != null) {
            urlBuilder.append("?after=").append(request.getAfter());
            hasParam = true;
        }
        if(request.getLimit() != null) {
            urlBuilder.append(hasParam ? "&" : "?").append("limit=").append(request.getLimit());
        }

        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(urlBuilder.toString())
                .get()
                .build();

        try {
            OpenAiResponse<Batch> response = HttpUtils.httpRequest(httpRequest,
                    new com.fasterxml.jackson.core.type.TypeReference<OpenAiResponse<Batch>>() {
                    });
            if(response == null) {
                throw new IllegalStateException("Failed to list batches: no response returned");
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to list batches from {}", url, e);
            throw new RuntimeException("Failed to list batches", e);
        }
    }

    @Override
    public Batch cancelBatch(String batchId, String url, OpenAiProperty property) {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), new byte[0]);
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(url + "/" + batchId + "/cancel")
                .post(requestBody)
                .build();

        try {
            Batch response = HttpUtils.httpRequest(httpRequest, Batch.class);
            if(response == null) {
                throw new RuntimeException("Failed to cancel batch: no response returned");
            }
            log.info("Cancelled batch: {}, status: {}", batchId, response.getStatus());
            return response;
        } catch (Exception e) {
            log.error("Failed to cancel batch: {}", batchId, e);
            throw new RuntimeException("Failed to cancel batch: " + batchId, e);
        }
    }

    @Override
    public String getDescription() {
        return "OpenAi Batch API协议";
    }

    @Override
    public Class<OpenAiProperty> getPropertyClass() {
        return OpenAiProperty.class;
    }

    @Data
    private static class FileUploadResponse {
        private String id;
    }
}
