package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.tts.TtsRequest;
import com.ke.bella.openapi.protocol.tts.TtsAdaptor;
import com.ke.bella.openapi.protocol.tts.TtsProperty;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.endpoints.testdata.SpeechHistoricalDataLoader;
import com.ke.bella.openapi.endpoints.testdata.TranscriptionHistoricalDataLoader;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionRequest.AudioTranscriptionReq;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionResponse.AudioTranscriptionResp;
import com.ke.bella.job.queue.JobQueueClient;
import com.ke.bella.job.queue.api.entity.param.TaskParam;
import com.ke.bella.job.queue.api.entity.response.TaskResp;
import com.ke.bella.job.queue.config.JobQueueProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * AudioController speech endpoint compatibility tests
 *
 * Bypasses Spring AOP, interceptors, filters to directly test Controller core business logic
 * Separates test data from test logic, managing historical request cases via external JSON files
 *
 * Core objective: Ensure historical API requests remain unaffected during code iterations
 */
@RunWith(MockitoJUnitRunner.class)
public class AudioControllerTest {

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private AdaptorManager adaptorManager;

    @Mock
    private LimiterManager limiterManager;

    @Mock
    private TtsAdaptor<TtsProperty> mockTtsAdaptor;

    @Mock
    private ContentCachingRequestWrapper mockWrappedRequest;

    @Mock
    private HttpServletRequest mockHttpRequest;

    @Mock
    private HttpServletResponse mockHttpResponse;

    @Mock
    private ServletOutputStream mockOutputStream;

    @Mock
    private AsyncContext mockAsyncContext;

    @Mock
    private JobQueueClient mockJobQueueClient;

    @Mock
    private JobQueueProperties mockJobQueueProperties;

    @InjectMocks
    private AudioController audioController;

    private MockedStatic<JobQueueClient> mockedJobQueueClient;

    @Before
    public void setUp() {
        setupBasicMockEnvironment();
        setupJobQueueClientMock();
        setupJobQueuePropertiesMock();
    }

    /**
     * Batch validate backward compatibility of all speech historical requests
     */
    @Test
    public void testAllSpeechHistoricalRequestsBackwardCompatibility() {
        System.out.println("=== Starting batch speech compatibility validation ===");

        // Load test data
        List<SpeechHistoricalDataLoader.SpeechTestCase> allCases =
            SpeechHistoricalDataLoader.loadSpeechRequests();

        int totalCases = allCases.size();
        int passedCases = 0;
        List<String> failedCases = new ArrayList<>();

        System.out.println("Loaded speech historical request scenarios: " + totalCases);

        for (SpeechHistoricalDataLoader.SpeechTestCase testCase : allCases) {
            try {
                System.out.println("--- Validating scenario: " + testCase.getScenarioName() + " ---");
                System.out.println("Scenario description: " + testCase.getDescription());

                // Execute single historical request test
                validateSingleSpeechHistoricalRequest(testCase);

                passedCases++;
                System.out.println("✅ " + testCase.getScenarioName() + " - Compatibility validation passed");

            } catch (Exception | AssertionError e) {
                failedCases.add(testCase.getScenarioName() + ": " + e.getMessage());
                System.err.println("❌ " + testCase.getScenarioName() + " - Compatibility validation failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("=== Batch speech compatibility validation results ===");
        System.out.println("Total test scenarios: " + totalCases);
        System.out.println("Passed scenarios: " + passedCases);
        System.out.println("Failed scenarios: " + failedCases.size());

        if (!failedCases.isEmpty()) {
            System.err.println("Failed scenario details:");
            failedCases.forEach(failure -> System.err.println("  - " + failure));
            fail("Found " + failedCases.size() + " speech historical request compatibility validation failures");
        }

        System.out.println("🎉 All speech historical request compatibility validations completed!");
    }

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

        System.out.println("=== Batch transcription compatibility validation results ===");
        System.out.println("Total test scenarios: " + totalCases);
        System.out.println("Passed scenarios: " + passedCases);
        System.out.println("Failed scenarios: " + failedCases.size());

        if (!failedCases.isEmpty()) {
            System.err.println("Failed scenario details:");
            failedCases.forEach(failure -> System.err.println("  - " + failure));
            fail("Found " + failedCases.size() + " transcription historical request compatibility validation failures");
        }

        System.out.println("🎉 All transcription historical request compatibility validations completed!");
    }

    /**
     * Validate single speech historical request scenario logic
     */
    private void validateSingleSpeechHistoricalRequest(SpeechHistoricalDataLoader.SpeechTestCase testCase) throws IOException {
        // 1. Setup request context
        setupSpeechRequestContext();

        // 2. Prepare test environment
        setupMockForSpeechTestCase(testCase);

        // 3. Execute Controller core logic (bypass all AOP)
        // Since speech endpoint returns void and writes to response, we validate through mocking
        audioController.speech(testCase.getRequest(), mockHttpRequest, mockHttpResponse);

        // 4. Validate response format compatibility
        validateSpeechResponseCompatibility(testCase);

        // 5. Validate underlying service call parameters
        validateSpeechServiceCallParameters(testCase);

        // 6. Reset Mock state for next test
        reset(channelRouter, adaptorManager, mockTtsAdaptor, mockHttpResponse);
    }

    /**
     * Setup Mock for speech test scenarios
     */
    private void setupMockForSpeechTestCase(SpeechHistoricalDataLoader.SpeechTestCase testCase) throws IOException {
        // Setup ChannelRouter Mock
        when(channelRouter.route(eq("/v1/audio/speech"), eq(testCase.getRequest().getModel()), any(), eq(false)))
            .thenReturn(testCase.getMockChannel());

        // Setup AdaptorManager Mock
        when(adaptorManager.getProtocolAdaptor(eq("/v1/audio/speech"),
                                              eq(testCase.getMockChannel().getProtocol()),
                                              eq(TtsAdaptor.class)))
            .thenReturn(mockTtsAdaptor);

        when(mockTtsAdaptor.getPropertyClass())
            .thenReturn((Class) TtsProperty.class);

        // Setup HTTP response mock
        when(mockHttpResponse.getOutputStream()).thenReturn(mockOutputStream);

        // Setup HTTP request mock for async operations
        when(mockHttpRequest.startAsync()).thenReturn(mockAsyncContext);
        doNothing().when(mockAsyncContext).setTimeout(anyLong());

        // Mock TTS adaptor response
        byte[] mockAudioData = new byte[testCase.getExpectedResponse().getDataLength()];
        if (testCase.getRequest().isStream()) {
            // For streaming requests, mock the streamTts method
            doNothing().when(mockTtsAdaptor).streamTts(any(TtsRequest.class), anyString(), any(TtsProperty.class), any());
        } else {
            // For non-streaming requests, mock the tts method
            when(mockTtsAdaptor.tts(any(TtsRequest.class), anyString(), any(TtsProperty.class)))
                .thenReturn(mockAudioData);
        }
    }

    /**
     * Validate speech response format compatibility
     */
    private void validateSpeechResponseCompatibility(SpeechHistoricalDataLoader.SpeechTestCase testCase) throws IOException {
        // Validate response content type was set
        verify(mockHttpResponse, times(1)).setContentType(testCase.getExpectedResponse().getContentType());

        // Validate the correct TTS method was called based on stream flag
        if (testCase.getRequest().isStream()) {
            verify(mockTtsAdaptor, times(1)).streamTts(
                any(TtsRequest.class),
                eq(testCase.getMockChannel().getUrl()),
                any(TtsProperty.class),
                any()
            );
        } else {
            verify(mockTtsAdaptor, times(1)).tts(
                any(TtsRequest.class),
                eq(testCase.getMockChannel().getUrl()),
                any(TtsProperty.class)
            );

            // For non-streaming, verify data was written to output stream
            verify(mockHttpResponse, times(1)).getOutputStream();
        }

        // Execute scenario-specific validation
        if (testCase.getCustomValidator() != null) {
            testCase.getCustomValidator().accept(testCase.getExpectedResponse());
        }
    }

    /**
     * Validate correctness of speech underlying service call parameters
     */
    private void validateSpeechServiceCallParameters(SpeechHistoricalDataLoader.SpeechTestCase testCase) {
        // Validate ChannelRouter call
        verify(channelRouter, times(1)).route(
            eq("/v1/audio/speech"),
            eq(testCase.getRequest().getModel()),
            any(), // API key
            eq(false) // Non-Mock mode
        );

        // Validate AdaptorManager call
        verify(adaptorManager, times(1)).getProtocolAdaptor(
            eq("/v1/audio/speech"),
            eq(testCase.getMockChannel().getProtocol()),
            eq(TtsAdaptor.class)
        );

        // Validate TTS request parameters
        assertTrue("Parameter validation failed for scenario: " + testCase.getScenarioName(),
                  testCase.getParameterValidator().test(testCase.getRequest()));
    }

    /**
     * Setup basic Mock environment
     */
    private void setupBasicMockEnvironment() {
        // Setup API key in BellaContext
        ApikeyInfo testApikey = new ApikeyInfo();
        testApikey.setApikey("test-key");
        ApikeyInfo.RolePath rolePath = new ApikeyInfo.RolePath();
        rolePath.getIncluded().add("/v1/**");
        testApikey.setRolePath(rolePath);
        BellaContext.setApikey(testApikey);
    }

    /**
     * Setup JobQueueClient static mock
     */
    private void setupJobQueueClientMock() {
        mockedJobQueueClient = Mockito.mockStatic(JobQueueClient.class);
        mockedJobQueueClient.when(() -> JobQueueClient.getInstance(anyString()))
                            .thenReturn(mockJobQueueClient);
    }

    /**
     * Setup JobQueueProperties mock
     */
    private void setupJobQueuePropertiesMock() {
        when(mockJobQueueProperties.getUrl()).thenReturn("http://mock-queue-service");
    }

    /**
     * Setup request context for speech tests
     */
    private void setupSpeechRequestContext() {
        when(mockWrappedRequest.getRequestURI()).thenReturn("/v1/audio/speech");
        EndpointContext.setRequest(mockWrappedRequest);
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
        when(mockWrappedRequest.getRequestURI()).thenReturn("/v1/audio/transcriptions/file");
        EndpointContext.setRequest(mockWrappedRequest);

        // Set up EndpointProcessData with the required apikey
        ApikeyInfo testApikey = BellaContext.getApikey();
        if (testApikey != null) {
            EndpointContext.getProcessData().setApikey(testApikey.getApikey());
        }
    }

    @After
    public void tearDown() {
        BellaContext.clearAll();
        EndpointContext.clearAll();
        if (mockedJobQueueClient != null) {
            mockedJobQueueClient.close();
        }
    }
}
