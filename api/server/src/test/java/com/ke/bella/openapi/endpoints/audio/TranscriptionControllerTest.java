package com.ke.bella.openapi.endpoints.audio;

import com.ke.bella.openapi.endpoints.testdata.TranscriptionHistoricalDataLoader;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionRequest.AudioTranscriptionReq;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionResponse.AudioTranscriptionResp;
import com.ke.bella.job.queue.api.entity.param.TaskParam;
import com.ke.bella.job.queue.api.entity.response.TaskResp;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Transcription endpoint compatibility tests
 *
 * Tests the /v1/audio/transcriptions/file endpoint for backward compatibility
 * with historical transcription requests across different providers
 */
public class TranscriptionControllerTest extends AudioControllerTestBase {

    /**
     * Batch validate backward compatibility of all transcription historical requests
     */
    @Test
    public void testAllTranscriptionHistoricalRequestsBackwardCompatibility() {
        System.out.println("=== Starting batch transcription compatibility validation ===");

        // Load test data
        List<TranscriptionHistoricalDataLoader.TranscriptionTestCase> allCases;
        try {
            allCases = TranscriptionHistoricalDataLoader.loadTranscriptionRequests();
        } catch (Exception e) {
            System.err.println("Failed to load transcription test data: " + e.getMessage());
            e.printStackTrace();
            fail("Failed to load transcription test data: " + e.getMessage());
            return;
        }

        if (allCases.isEmpty()) {
            System.out.println("No transcription test data found, skipping transcription compatibility validation");
            return;
        }

        int totalCases = allCases.size();
        int passedCases = 0;
        List<String> failedCases = new ArrayList<>();

        System.out.println("Loaded transcription historical request scenarios: " + totalCases);

        for (TranscriptionHistoricalDataLoader.TranscriptionTestCase testCase : allCases) {
            try {
                System.out.println("--- Validating scenario: " + testCase.getScenarioName() + " ---");
                System.out.println("Scenario description: " + testCase.getDescription());

                // Execute single historical request test
                validateSingleTranscriptionHistoricalRequest(testCase);

                passedCases++;
                System.out.println("✅ " + testCase.getScenarioName() + " - Compatibility validation passed");

            } catch (Exception | AssertionError e) {
                failedCases.add(testCase.getScenarioName() + ": " + e.getMessage());
                System.err.println("❌ " + testCase.getScenarioName() + " - Compatibility validation failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        printTestSummary("Transcription", totalCases, passedCases, failedCases);
    }

    /**
     * Validate single transcription historical request scenario logic
     */
    private void validateSingleTranscriptionHistoricalRequest(TranscriptionHistoricalDataLoader.TranscriptionTestCase testCase) {
        // 1. Setup request context
        setupTranscriptionRequestContext();

        // 2. Prepare test environment
        setupMockForTranscriptionTestCase(testCase);

        // 3. Execute Controller core logic (bypass all AOP)
        AudioTranscriptionResp actualResponse = audioController.transcribeAudio(testCase.getRequest());

        // 4. Validate response format compatibility
        validateTranscriptionResponseCompatibility(testCase, actualResponse);

        // 5. Validate underlying service call parameters
        validateTranscriptionServiceCallParameters(testCase);

        // 6. Reset Mock state for next test
        reset(mockJobQueueClient, mockJobQueueProperties);
    }

    /**
     * Setup Mock for transcription test scenarios
     */
    private void setupMockForTranscriptionTestCase(TranscriptionHistoricalDataLoader.TranscriptionTestCase testCase) {
        // Setup JobQueueProperties Mock
        when(mockJobQueueProperties.getUrl()).thenReturn("http://mock-queue-service");

        // Setup JobQueueClient Mock
        TaskResp.TaskPutResp mockTaskPutResp = TaskResp.TaskPutResp.builder()
            .taskId(testCase.getExpectedResponse().getTaskId())
            .build();

        when(mockJobQueueClient.put(any(TaskParam.TaskPutParam.class), any(String.class), eq(TaskResp.TaskPutResp.class)))
            .thenReturn(mockTaskPutResp);
        when(mockJobQueueClient.buildTaskPutRequest(any(AudioTranscriptionReq.class), eq(null), anyString(), anyString()))
            .thenReturn(new TaskParam.TaskPutParam());
    }

    /**
     * Validate transcription response format compatibility
     */
    private void validateTranscriptionResponseCompatibility(TranscriptionHistoricalDataLoader.TranscriptionTestCase testCase,
                                                          AudioTranscriptionResp actualResponse) {
        assertNotNull(testCase.getScenarioName() + " - Response cannot be null", actualResponse);
        assertNotNull(testCase.getScenarioName() + " - Response must contain task_id field", actualResponse.getTaskId());
        assertFalse(testCase.getScenarioName() + " - Task ID cannot be empty", actualResponse.getTaskId().isEmpty());

        // Validate task_id matches expected format/value
        assertEquals(testCase.getScenarioName() + " - Task ID must match expected value",
                    testCase.getExpectedResponse().getTaskId(),
                    actualResponse.getTaskId());

        // Execute scenario-specific validation
        if (testCase.getCustomValidator() != null) {
            testCase.getCustomValidator().accept(actualResponse);
        }
    }

    /**
     * Validate correctness of transcription underlying service call parameters
     */
    private void validateTranscriptionServiceCallParameters(TranscriptionHistoricalDataLoader.TranscriptionTestCase testCase) {
        // Validate JobQueueClient interactions
        verify(mockJobQueueClient, times(1)).buildTaskPutRequest(
            argThat(req -> req instanceof AudioTranscriptionReq && testCase.getParameterValidator().test((AudioTranscriptionReq) req)),
            eq(null),
            eq("/v1/audio/transcriptions/file"),
            eq(testCase.getRequest().getModel())
        );

        verify(mockJobQueueClient, times(1)).put(
            any(TaskParam.TaskPutParam.class),
            any(String.class),
            eq(TaskResp.TaskPutResp.class)
        );

        // Validate request parameter validation passed
        assertTrue("Parameter validation failed for scenario: " + testCase.getScenarioName(),
                  testCase.getParameterValidator().test(testCase.getRequest()));
    }

    /**
     * Setup request context for transcription tests
     */
    private void setupTranscriptionRequestContext() {
        setupRequestContext("/v1/audio/transcriptions/file");
    }
}