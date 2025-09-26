package com.ke.bella.openapi.endpoints.audio;

import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingRequest;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingResponse;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingAdaptor;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingProperty;
import com.ke.bella.openapi.endpoints.testdata.SpeakerEmbeddingHistoricalDataLoader;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Speaker embedding endpoint compatibility tests
 *
 * Tests the /v1/audio/speaker/embedding endpoint for backward compatibility
 * with historical speaker embedding requests across different providers
 */
public class SpeakerEmbeddingControllerTest extends AudioControllerTestBase {

    @Mock
    private SpeakerEmbeddingAdaptor mockSpeakerEmbeddingAdaptor;

    /**
     * Batch validate backward compatibility of all speaker embedding historical requests
     */
    @Test
    public void testAllSpeakerEmbeddingHistoricalRequestsBackwardCompatibility() {
        System.out.println("=== Starting batch speaker embedding compatibility validation ===");

        // Load test data
        List<SpeakerEmbeddingHistoricalDataLoader.SpeakerEmbeddingTestCase> allCases =
            SpeakerEmbeddingHistoricalDataLoader.loadSpeakerEmbeddingRequests();

        int totalCases = allCases.size();
        int passedCases = 0;
        List<String> failedCases = new ArrayList<>();

        System.out.println("Loaded speaker embedding historical request scenarios: " + totalCases);

        for (SpeakerEmbeddingHistoricalDataLoader.SpeakerEmbeddingTestCase testCase : allCases) {
            try {
                System.out.println("--- Validating scenario: " + testCase.getScenarioName() + " ---");
                System.out.println("Scenario description: " + testCase.getDescription());

                // Execute single historical request test
                validateSingleSpeakerEmbeddingHistoricalRequest(testCase);

                passedCases++;
                System.out.println("✅ " + testCase.getScenarioName() + " - Compatibility validation passed");

            } catch (Exception | AssertionError e) {
                failedCases.add(testCase.getScenarioName() + ": " + e.getMessage());
                System.err.println("❌ " + testCase.getScenarioName() + " - Compatibility validation failed: " + e.getMessage());
                e.printStackTrace(); // Print complete stack trace
            }
        }

        // Print summary and assert results
        printTestSummary("Speaker embedding", totalCases, passedCases, failedCases);
    }

    /**
     * Validate single speaker embedding historical request scenario logic
     */
    private void validateSingleSpeakerEmbeddingHistoricalRequest(SpeakerEmbeddingHistoricalDataLoader.SpeakerEmbeddingTestCase testCase) {
        // 1. Setup request context
        setupSpeakerEmbeddingRequestContext();

        // 2. Prepare test environment
        setupMockForSpeakerEmbeddingTestCase(testCase);

        // 3. Execute Controller core logic (bypass all AOP)
        SpeakerEmbeddingResponse actualResponse = audioController.speakerEmbedding(testCase.getRequest());

        // 4. Validate response format compatibility
        validateSpeakerEmbeddingResponseCompatibility(testCase, actualResponse);

        // 5. Validate underlying service call parameters
        validateSpeakerEmbeddingServiceCallParameters(testCase);

        // 6. Reset Mock state for next test
        reset(channelRouter, adaptorManager, mockSpeakerEmbeddingAdaptor);
    }

    /**
     * Setup request context for speaker embedding
     */
    private void setupSpeakerEmbeddingRequestContext() {
        setupRequestContext("/v1/audio/speaker/embedding");
    }

    /**
     * Setup mock environment for speaker embedding test case
     */
    private void setupMockForSpeakerEmbeddingTestCase(SpeakerEmbeddingHistoricalDataLoader.SpeakerEmbeddingTestCase testCase) {
        // Setup ChannelRouter Mock
        when(channelRouter.route(eq("/v1/audio/speaker/embedding"), eq(testCase.getRequest().getModel()), any(), eq(false)))
            .thenReturn(testCase.getMockChannel());

        // Setup AdaptorManager Mock
        when(adaptorManager.getProtocolAdaptor(eq("/v1/audio/speaker/embedding"),
                                              eq(testCase.getMockChannel().getProtocol()),
                                              eq(SpeakerEmbeddingAdaptor.class)))
            .thenReturn(mockSpeakerEmbeddingAdaptor);

        when(mockSpeakerEmbeddingAdaptor.getPropertyClass())
            .thenReturn((Class) SpeakerEmbeddingProperty.class);

        // Setup Adaptor Mock response
        when(mockSpeakerEmbeddingAdaptor.speakerEmbedding(any(SpeakerEmbeddingRequest.class),
                                                        eq(testCase.getMockChannel().getUrl()),
                                                        any(SpeakerEmbeddingProperty.class)))
            .thenReturn(testCase.getExpectedResponse());
    }

    /**
     * Validate speaker embedding response format compatibility
     */
    private void validateSpeakerEmbeddingResponseCompatibility(SpeakerEmbeddingHistoricalDataLoader.SpeakerEmbeddingTestCase testCase, SpeakerEmbeddingResponse actualResponse) {
        // Validate request parameters
        assertTrue("Request parameter validation should pass", testCase.getParameterValidator().test(testCase.getRequest()));

        // Basic response validations
        assertNotNull("Response should not be null", actualResponse);

        // Run custom validations
        testCase.getCustomValidator().accept(actualResponse);
    }

    /**
     * Validate speaker embedding service call parameters
     */
    private void validateSpeakerEmbeddingServiceCallParameters(SpeakerEmbeddingHistoricalDataLoader.SpeakerEmbeddingTestCase testCase) {
        // Verify Mock interactions
        verify(channelRouter).route(eq("/v1/audio/speaker/embedding"), eq(testCase.getRequest().getModel()), any(), eq(false));
        verify(adaptorManager).getProtocolAdaptor(eq("/v1/audio/speaker/embedding"), eq(testCase.getMockChannel().getProtocol()), eq(SpeakerEmbeddingAdaptor.class));
        verify(mockSpeakerEmbeddingAdaptor).speakerEmbedding(eq(testCase.getRequest()), eq(testCase.getMockChannel().getUrl()), any(SpeakerEmbeddingProperty.class));
    }

    /**
     * Test specific speaker embedding scenarios
     */
    @Test
    public void testSpeakerEmbeddingBasicUrlRequest() {
        System.out.println("=== Testing basic speaker embedding with URL ===");

        // Setup request context
        setupRequestContext("/v1/audio/speaker/embedding");

        // Create test request
        SpeakerEmbeddingRequest request = new SpeakerEmbeddingRequest();
        request.setModel("ke-speaker-embedding-v1");
        request.setUrl("https://test-audio-bucket.s3.amazonaws.com/sample-speech.wav");
        request.setNormalize(true);
        request.setSampleRate(16000);
        request.setTaskId("test_task_001");

        // Setup mock channel
        ChannelDB mockChannel = new ChannelDB();
        mockChannel.setProtocol("ke");
        mockChannel.setUrl("https://api.speaker-embedding.com/v1");
        mockChannel.setChannelInfo("{\"encodingType\": \"json\"}");

        // Setup mock expected response
        SpeakerEmbeddingResponse expectedResponse = new SpeakerEmbeddingResponse();
        expectedResponse.setTask("speaker_embedding");
        expectedResponse.setTaskId("test_task_001");
        expectedResponse.setDuration(5.123);
        expectedResponse.setDimensions(512);

        List<SpeakerEmbeddingResponse.Embedding> embeddings = new ArrayList<>();
        SpeakerEmbeddingResponse.Embedding embedding = new SpeakerEmbeddingResponse.Embedding();
        embedding.setId(0);
        embedding.setStart(0.0);
        embedding.setEnd(5.123);
        embedding.setConfidence(0.95);
        embedding.setEmbedding(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5));
        embeddings.add(embedding);
        expectedResponse.setEmbeddings(embeddings);

        // Setup mocks
        when(channelRouter.route(eq("/v1/audio/speaker/embedding"), eq(request.getModel()), any(), eq(false)))
            .thenReturn(mockChannel);
        when(adaptorManager.getProtocolAdaptor(eq("/v1/audio/speaker/embedding"), eq(mockChannel.getProtocol()), eq(SpeakerEmbeddingAdaptor.class)))
            .thenReturn(mockSpeakerEmbeddingAdaptor);
        when(mockSpeakerEmbeddingAdaptor.getPropertyClass())
            .thenReturn((Class) SpeakerEmbeddingProperty.class);
        when(mockSpeakerEmbeddingAdaptor.speakerEmbedding(any(SpeakerEmbeddingRequest.class), eq(mockChannel.getUrl()), any(SpeakerEmbeddingProperty.class)))
            .thenReturn(expectedResponse);

        // Execute request
        SpeakerEmbeddingResponse response = audioController.speakerEmbedding(request);

        // Assertions
        assertNotNull("Response should not be null", response);
        assertEquals("Task should be speaker_embedding", "speaker_embedding", response.getTask());
        assertEquals("Task ID should match", "test_task_001", response.getTaskId());
        assertEquals("Duration should match", 5.123, response.getDuration(), 0.001);
        assertEquals("Dimensions should be 512", 512, response.getDimensions());
        assertNotNull("Embeddings should not be null", response.getEmbeddings());
        assertFalse("Embeddings should not be empty", response.getEmbeddings().isEmpty());

        // Verify interactions
        verify(channelRouter).route(eq("/v1/audio/speaker/embedding"), eq("ke-speaker-embedding-v1"), any(), eq(false));
        verify(adaptorManager).getProtocolAdaptor(eq("/v1/audio/speaker/embedding"), eq("ke"), eq(SpeakerEmbeddingAdaptor.class));
        verify(mockSpeakerEmbeddingAdaptor).speakerEmbedding(eq(request), eq("https://api.speaker-embedding.com/v1"), any(SpeakerEmbeddingProperty.class));

        System.out.println("✅ Basic speaker embedding URL test passed");
    }

    /**
     * Test speaker embedding with base64 audio
     */
    @Test
    public void testSpeakerEmbeddingBase64Request() {
        System.out.println("=== Testing speaker embedding with base64 audio ===");

        // Setup request context
        setupRequestContext("/v1/audio/speaker/embedding");

        // Create test request
        SpeakerEmbeddingRequest request = new SpeakerEmbeddingRequest();
        request.setModel("ke-speaker-embedding-v1");
        request.setBase64("UklGRiQAAABXQVZFZm10IBAAAAABAAEAIlYAAESsAAACABAAZGF0YQAAAAA=");
        request.setNormalize(false);
        request.setSampleRate(22050);
        request.setTaskId("test_task_002");

        // Setup mock channel
        ChannelDB mockChannel = new ChannelDB();
        mockChannel.setProtocol("ke");
        mockChannel.setUrl("https://api.speaker-embedding.com/v1");
        mockChannel.setChannelInfo("{\"encodingType\": \"json\"}");

        // Setup mock expected response
        SpeakerEmbeddingResponse expectedResponse = new SpeakerEmbeddingResponse();
        expectedResponse.setTask("speaker_embedding");
        expectedResponse.setTaskId("test_task_002");
        expectedResponse.setDuration(3.456);
        expectedResponse.setDimensions(512);

        // Setup mocks
        when(channelRouter.route(eq("/v1/audio/speaker/embedding"), eq(request.getModel()), any(), eq(false)))
            .thenReturn(mockChannel);
        when(adaptorManager.getProtocolAdaptor(eq("/v1/audio/speaker/embedding"), eq(mockChannel.getProtocol()), eq(SpeakerEmbeddingAdaptor.class)))
            .thenReturn(mockSpeakerEmbeddingAdaptor);
        when(mockSpeakerEmbeddingAdaptor.getPropertyClass())
            .thenReturn((Class) SpeakerEmbeddingProperty.class);
        when(mockSpeakerEmbeddingAdaptor.speakerEmbedding(any(SpeakerEmbeddingRequest.class), eq(mockChannel.getUrl()), any(SpeakerEmbeddingProperty.class)))
            .thenReturn(expectedResponse);

        // Execute request
        SpeakerEmbeddingResponse response = audioController.speakerEmbedding(request);

        // Assertions
        assertNotNull("Response should not be null", response);
        assertEquals("Task should be speaker_embedding", "speaker_embedding", response.getTask());
        assertEquals("Task ID should match", "test_task_002", response.getTaskId());
        assertEquals("Duration should match", 3.456, response.getDuration(), 0.001);

        // Verify interactions
        verify(channelRouter).route(eq("/v1/audio/speaker/embedding"), eq("ke-speaker-embedding-v1"), any(), eq(false));
        verify(mockSpeakerEmbeddingAdaptor).speakerEmbedding(eq(request), eq("https://api.speaker-embedding.com/v1"), any(SpeakerEmbeddingProperty.class));

        System.out.println("✅ Speaker embedding base64 test passed");
    }

    /**
     * Test speaker embedding with VAD (Voice Activity Detection) enabled
     */
    @Test
    public void testSpeakerEmbeddingWithVadRequest() {
        System.out.println("=== Testing speaker embedding with VAD enabled ===");

        // Setup request context
        setupRequestContext("/v1/audio/speaker/embedding");

        // Create test request with VAD parameters
        SpeakerEmbeddingRequest request = new SpeakerEmbeddingRequest();
        request.setModel("ke-speaker-embedding-v1");
        request.setUrl("https://test-audio-bucket.s3.amazonaws.com/long-speech.wav");
        request.setNormalize(true);
        request.setSampleRate(16000);
        request.setTaskId("test_task_003");
        request.setEnableVad(true);
        request.setVadAggressiveness(2);
        request.setMinSpeechDuration(1.5);
        request.setMaxSilenceDuration(0.8);

        // Setup mock channel
        ChannelDB mockChannel = new ChannelDB();
        mockChannel.setProtocol("ke");
        mockChannel.setUrl("https://api.speaker-embedding.com/v1");
        mockChannel.setChannelInfo("{\"encodingType\": \"json\"}");

        // Setup mock expected response with multiple embeddings
        SpeakerEmbeddingResponse expectedResponse = new SpeakerEmbeddingResponse();
        expectedResponse.setTask("speaker_embedding");
        expectedResponse.setTaskId("test_task_003");
        expectedResponse.setDuration(12.789);
        expectedResponse.setDimensions(512);

        List<SpeakerEmbeddingResponse.Embedding> embeddings = new ArrayList<>();

        SpeakerEmbeddingResponse.Embedding embedding1 = new SpeakerEmbeddingResponse.Embedding();
        embedding1.setId(0);
        embedding1.setStart(0.5);
        embedding1.setEnd(4.2);
        embedding1.setConfidence(0.92);
        embedding1.setEmbedding(Arrays.asList(0.3, 0.1, 0.8, 0.2, 0.7));
        embeddings.add(embedding1);

        SpeakerEmbeddingResponse.Embedding embedding2 = new SpeakerEmbeddingResponse.Embedding();
        embedding2.setId(1);
        embedding2.setStart(5.1);
        embedding2.setEnd(8.6);
        embedding2.setConfidence(0.89);
        embedding2.setEmbedding(Arrays.asList(0.4, 0.9, 0.1, 0.6, 0.3));
        embeddings.add(embedding2);

        expectedResponse.setEmbeddings(embeddings);

        // Setup mocks
        when(channelRouter.route(eq("/v1/audio/speaker/embedding"), eq(request.getModel()), any(), eq(false)))
            .thenReturn(mockChannel);
        when(adaptorManager.getProtocolAdaptor(eq("/v1/audio/speaker/embedding"), eq(mockChannel.getProtocol()), eq(SpeakerEmbeddingAdaptor.class)))
            .thenReturn(mockSpeakerEmbeddingAdaptor);
        when(mockSpeakerEmbeddingAdaptor.getPropertyClass())
            .thenReturn((Class) SpeakerEmbeddingProperty.class);
        when(mockSpeakerEmbeddingAdaptor.speakerEmbedding(any(SpeakerEmbeddingRequest.class), eq(mockChannel.getUrl()), any(SpeakerEmbeddingProperty.class)))
            .thenReturn(expectedResponse);

        // Execute request
        SpeakerEmbeddingResponse response = audioController.speakerEmbedding(request);

        // Assertions
        assertNotNull("Response should not be null", response);
        assertEquals("Task should be speaker_embedding", "speaker_embedding", response.getTask());
        assertEquals("Task ID should match", "test_task_003", response.getTaskId());
        assertEquals("Duration should match", 12.789, response.getDuration(), 0.001);
        assertNotNull("Embeddings should not be null", response.getEmbeddings());
        assertEquals("Should have 2 embeddings", 2, response.getEmbeddings().size());

        // Verify first embedding
        SpeakerEmbeddingResponse.Embedding firstEmbedding = response.getEmbeddings().get(0);
        assertEquals("First embedding start time", 0.5, firstEmbedding.getStart(), 0.001);
        assertEquals("First embedding end time", 4.2, firstEmbedding.getEnd(), 0.001);
        assertEquals("First embedding confidence", 0.92, firstEmbedding.getConfidence(), 0.001);

        // Verify interactions
        verify(channelRouter).route(eq("/v1/audio/speaker/embedding"), eq("ke-speaker-embedding-v1"), any(), eq(false));
        verify(mockSpeakerEmbeddingAdaptor).speakerEmbedding(eq(request), eq("https://api.speaker-embedding.com/v1"), any(SpeakerEmbeddingProperty.class));

        System.out.println("✅ Speaker embedding with VAD test passed");
    }
}