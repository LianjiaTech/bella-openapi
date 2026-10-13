package com.ke.bella.openapi.protocol.metrics;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class VoicePrometheusRecorderTest {
    private SimpleMeterRegistry registry;
    private VoicePrometheusRecorder recorder;

    @Before
    public void setUp() {
        registry = new SimpleMeterRegistry();
        recorder = new VoicePrometheusRecorder(registry);
    }

    @Test
    public void recordsRequestWithLowCardinalityTagsAndTimings() {
        EndpointProcessData processData = processData("request-a", "/v1/audio/speech");
        processData.setRequestMillis(1000);
        processData.setForwardMillis(1200);
        processData.setResponseMillis(2200);

        recorder.finish(processData);

        Counter counter = registry.find("bella_voice_requests")
                .tags("api_endpoint", "/v1/audio/speech", "status_class", "success", "http_status_code", "200", "error_source", "none")
                .counter();
        assertNotNull(counter);
        assertEquals(1, counter.count(), 0.001);
        assertEquals(1200, registry.find("bella_voice_system_ttlt_milliseconds").summary().totalAmount(), 0.001);
        assertEquals(1000, registry.find("bella_voice_channel_ttlt_milliseconds").summary().totalAmount(), 0.001);
        assertNull(counter.getId().getTag("request_id"));
        assertNull(counter.getId().getTag("akCode"));
    }

    @Test
    public void recordsRouteFailureWithoutChannelLatency() {
        EndpointProcessData processData = processData("route", "/v1/audio/asr/flash");
        processData.setFailureStage(EndpointProcessData.FailureStage.ROUTING);
        processData.setResponse(errorResponse(503));
        processData.setRequestMillis(1000);
        processData.setForwardMillis(1000);
        processData.setResponseMillis(1500);

        recorder.finish(processData);

        assertNotNull(registry.find("bella_voice_requests").tags("status_class", "route_failure").counter());
        assertNull(registry.find("bella_voice_channel_ttlt_milliseconds").summary());
    }

    @Test
    public void hidesUnroutedModelFromMetricTags() {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/audio/asr/flash");
        processData.setModel("user-provided-random-model");
        processData.setFailureStage(EndpointProcessData.FailureStage.ROUTING);
        processData.setResponse(errorResponse(503));
        processData.setRequestMillis(1000);
        processData.setResponseMillis(1500);

        recorder.finish(processData);

        assertNotNull(registry.find("bella_voice_requests")
                .tag("model", "unknown")
                .counter());
        assertNull(registry.find("bella_voice_requests")
                .tag("model", "user-provided-random-model")
                .counter());
    }

    @Test
    public void usesPublicTranscriptionsEndpointWithoutMutatingRouteEndpoint() {
        EndpointProcessData processData = processData("transcriptions", "/v1/audio/asr/flash");
        processData.getMetrics().put(VoiceMetricSupport.PUBLIC_ENDPOINT_METRIC, "/v1/audio/transcriptions");

        recorder.finish(processData);

        assertNotNull(registry.find("bella_voice_requests").tag("api_endpoint", "/v1/audio/transcriptions").counter());
        assertEquals("/v1/audio/asr/flash", processData.getEndpoint());
    }

    @Test
    public void ignoresMockRequests() {
        EndpointProcessData processData = processData("mock-request", "/v1/audio/asr/stream");
        processData.setMock(true);

        recorder.finish(processData);

        assertNull(registry.find("bella_voice_requests").counter());
    }

    private EndpointProcessData processData(String requestId, String endpoint) {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestId(requestId);
        processData.setEndpoint(endpoint);
        processData.setModel("voice-model");
        processData.setSupplier("supplier-a");
        processData.setChannelCode("channel-a");
        processData.setDeployName("deploy-a");
        processData.setForwardHost("provider.example");
        processData.setForwardPath("/voice");
        processData.setProtocol("test");
        return processData;
    }

    private OpenapiResponse errorResponse(int httpCode) {
        OpenapiResponse response = new OpenapiResponse();
        response.setError(OpenapiResponse.OpenapiError.builder().httpCode(httpCode).build());
        return response;
    }
}
