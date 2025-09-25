package com.ke.bella.openapi.endpoints.audio;

import com.ke.bella.openapi.protocol.tts.TtsRequest;
import com.ke.bella.openapi.protocol.tts.TtsAdaptor;
import com.ke.bella.openapi.protocol.tts.TtsProperty;
import com.ke.bella.openapi.endpoints.testdata.SpeechHistoricalDataLoader;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Speech/TTS endpoint compatibility tests
 *
 * Tests the /v1/audio/speech endpoint for backward compatibility
 * with historical TTS requests across different providers
 */
public class SpeechControllerTest extends AudioControllerTestBase {

    @Mock
    private TtsAdaptor<TtsProperty> mockTtsAdaptor;

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

        printTestSummary("Speech", totalCases, passedCases, failedCases);
    }

    /**
     * Validate single speech historical request scenario logic
     */
    private void validateSingleSpeechHistoricalRequest(SpeechHistoricalDataLoader.SpeechTestCase testCase) throws Exception {
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
    private void setupMockForSpeechTestCase(SpeechHistoricalDataLoader.SpeechTestCase testCase) throws Exception {
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
        setupHttpResponseMock();

        // Setup HTTP request mock for async operations
        setupAsyncHttpRequestMock();

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
     * Setup request context for speech tests
     */
    private void setupSpeechRequestContext() {
        setupRequestContext("/v1/audio/speech");
    }
}