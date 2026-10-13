package com.ke.bella.openapi.protocol.batch;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.queue.Task;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class VertexBatchAdaptorTest {

    private VertexBatchAdaptor adaptor;

    @Before
    public void setUp() {
        adaptor = new VertexBatchAdaptor();
    }

    @Test
    public void contract_usesBatchEndpointAndVertexBatchProperty() {
        assertEquals("/v1/batches", adaptor.endpoint());
        assertSame(VertexBatchProperty.class, adaptor.getPropertyClass());
        assertEquals("Vertex AI BatchPredictionJob + GCS协议", adaptor.getDescription());
    }

    @Test
    public void vertexRequestBody_convertsOpenAiBodyAndCarriesTaskIdLabel() {
        Task task = new Task();
        task.setTaskId("task-1");
        task.setData(chatCompletionBody("hello"));

        Map<String, Object> request = invoke("vertexRequestBody",
                new Class<?>[] { Task.class, VertexBatchProperty.class }, task, property());

        Map<String, Object> labels = cast(request.get("labels"));
        assertEquals("task-1", labels.get("bella_task_id"));

        List<Map<String, Object>> contents = cast(request.get("contents"));
        assertEquals("user", contents.get(0).get("role"));
        List<Map<String, Object>> parts = cast(contents.get(0).get("parts"));
        assertEquals("hello", parts.get(0).get("text"));
    }

    @Test
    public void toWorkerResult_preservesOpenAiCompatibleResponse() {
        String line = "{\"custom_id\":\"task-1\",\"response\":{\"status_code\":200,"
                + "\"body\":{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}}}";

        String workerResult = invoke("toWorkerResult", new Class<?>[] { String.class }, line);
        Map<String, Object> result = JacksonUtils.toMap(workerResult);

        assertEquals("task-1", result.get("custom_id"));
        Map<String, Object> response = cast(result.get("response"));
        assertEquals(200, response.get("status_code"));
        Map<String, Object> body = cast(response.get("body"));
        assertTrue(body.containsKey("choices"));
    }

    @Test
    public void toWorkerResult_readsTaskIdFromVertexRequestLabelAndMapsStatusToError() {
        String line = "{\"status\":\"Bad Request: invalid argument\","
                + "\"request\":{\"labels\":{\"bella_task_id\":\"task-1\"}},\"response\":{}}";

        String workerResult = invoke("toWorkerResult", new Class<?>[] { String.class }, line);
        Map<String, Object> result = JacksonUtils.toMap(workerResult);

        assertEquals("task-1", result.get("custom_id"));
        Map<String, Object> response = cast(result.get("response"));
        assertEquals(500, response.get("status_code"));
        assertEquals("Bad Request: invalid argument", response.get("error"));
    }

    @Test
    public void toWorkerResult_returnsNullWhenCustomIdCannotBeResolved() {
        assertNull(invoke("toWorkerResult", new Class<?>[] { String.class }, "{\"response\":{}}"));
    }

    @Test
    public void convertStatus_mapsVertexStatesToOpenAiBatchStatuses() {
        assertEquals("validating", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_PENDING", "default"));
        assertEquals("validating", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_QUEUED", "default"));
        assertEquals("in_progress", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_RUNNING", "default"));
        assertEquals("in_progress", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_UPDATING", "default"));
        assertEquals("cancelling", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_CANCELLING", "default"));
        assertEquals("completed", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_SUCCEEDED", "default"));
        assertEquals("failed", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_FAILED", "default"));
        assertEquals("cancelled", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_CANCELLED", "default"));
        assertEquals("expired", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_EXPIRED", "default"));
        assertEquals("default", invoke("convertStatus", new Class<?>[] { String.class, String.class }, "", "default"));
    }

    @Test
    public void convertStatus_rejectsUnknownState() {
        try {
            invoke("convertStatus", new Class<?>[] { String.class, String.class }, "JOB_STATE_UNKNOWN", "default");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Unsupported Vertex batch job state"));
            return;
        }
        throw new AssertionError("expected unknown state to fail");
    }

    @Test
    public void uriHelpers_normalizePrefixesAndGcsUris() {
        assertEquals("", invoke("normalizePrefix", new Class<?>[] { String.class }, ""));
        assertEquals("input/", invoke("normalizePrefix", new Class<?>[] { String.class }, "/input"));
        assertEquals("input/", invoke("normalizePrefix", new Class<?>[] { String.class }, "input/"));
        assertEquals("gs://bucket-a/path/file.jsonl", invoke("gcsUri", new Class<?>[] { String.class, String.class }, "bucket-a", "/path/file.jsonl"));
        assertEquals("run-1", invoke("runId", new Class<?>[] { String.class }, "gs://bucket-a/input/run-1.jsonl"));
        assertEquals("http://vertex/v1/jobs", invoke("batchJobsUrl", new Class<?>[] { String.class }, "http://vertex/v1/jobs/"));
    }

    @Test
    public void parseGcsUri_supportsFullUriAndObjectPath() {
        Object full = invoke("parseGcsUri", new Class<?>[] { String.class, String.class }, "gs://bucket-a/output/run-1/", null);
        assertEquals("bucket-a", invokeOn(full, "getBucket"));
        assertEquals("output/run-1/", invokeOn(full, "getObject"));

        Object objectPath = invoke("parseGcsUri", new Class<?>[] { String.class, String.class }, "/output/run-2/", "bucket-b");
        assertEquals("bucket-b", invokeOn(objectPath, "getBucket"));
        assertEquals("output/run-2/", invokeOn(objectPath, "getObject"));
    }

    @Test
    public void outputUri_prefersOutputInfoThenOutputConfigThenDefault() {
        Map<String, Object> outputInfoResponse = new HashMap<>();
        outputInfoResponse.put("outputInfo", Collections.singletonMap("gcsOutputDirectory", "gs://bucket/output-info/"));
        assertEquals("gs://bucket/output-info/", invoke("outputUri", new Class<?>[] { Map.class, String.class },
                outputInfoResponse, "gs://bucket/default/"));

        Map<String, Object> outputConfigResponse = new HashMap<>();
        outputConfigResponse.put("outputConfig", Collections.singletonMap("gcsDestination",
                Collections.singletonMap("outputUriPrefix", "gs://bucket/output-config/")));
        assertEquals("gs://bucket/output-config/", invoke("outputUri", new Class<?>[] { Map.class, String.class },
                outputConfigResponse, "gs://bucket/default/"));

        assertEquals("gs://bucket/default/", invoke("outputUri", new Class<?>[] { Map.class, String.class },
                Collections.emptyMap(), "gs://bucket/default/"));
    }

    @Test
    public void toBatch_setsOpenAiCompatibleFields() {
        Batch batch = invoke("toBatch", new Class<?>[] { String.class, String.class, String.class },
                "batch-1", "completed", "gs://bucket/output/");

        assertEquals("batch-1", batch.getId());
        assertEquals("completed", batch.getStatus());
        assertEquals("gs://bucket/output/", batch.getOutputFileId());
    }

    @Test
    public void requestBuilder_usesApiKeyOrBearerHeaders() {
        AuthorizationProperty apiKeyAuth = new AuthorizationProperty();
        apiKeyAuth.setApiKey("api-key");
        Request.Builder apiKeyBuilder = invoke("requestBuilder", new Class<?>[] { AuthorizationProperty.class }, apiKeyAuth);
        Request apiKeyRequest = apiKeyBuilder.url("http://localhost").build();
        assertEquals("api-key", apiKeyRequest.header("x-goog-api-key"));
        assertNull(apiKeyRequest.header("Authorization"));

        AuthorizationProperty bearerAuth = new AuthorizationProperty();
        bearerAuth.setType(AuthorizationProperty.AuthType.BEARER);
        bearerAuth.setApiKey("token");
        Request.Builder bearerBuilder = invoke("requestBuilder", new Class<?>[] { AuthorizationProperty.class }, bearerAuth);
        Request bearerRequest = bearerBuilder.url("http://localhost").build();
        assertEquals("Bearer token", bearerRequest.header("Authorization"));
        assertNull(bearerRequest.header("x-goog-api-key"));
    }

    private VertexBatchProperty property() {
        VertexBatchProperty property = new VertexBatchProperty();
        property.setSupportSystemInstruction(true);
        property.setSupportThinkConfig(false);
        return property;
    }

    private Map<String, Object> chatCompletionBody(String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", content);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gemini-2.5-flash");
        body.put("messages", Collections.singletonList(message));
        return body;
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = VertexBatchAdaptor.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (T) method.invoke(adaptor, args);
        } catch (InvocationTargetException e) {
            if(e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeOn(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (T) method.invoke(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cast(Object value) {
        return (T) value;
    }
}
