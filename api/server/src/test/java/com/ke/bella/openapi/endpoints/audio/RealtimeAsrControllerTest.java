package com.ke.bella.openapi.endpoints.audio;

import com.ke.bella.openapi.endpoints.testdata.RealtimeAsrHistoricalDataLoader;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.realtime.RealTimeAdaptor;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Realtime ASR endpoint compatibility tests
 *
 * Tests the /v1/audio/realtime and /v1/audio/asr/stream endpoints
 * for backward compatibility with historical WebSocket requests
 */
public class RealtimeAsrControllerTest extends AudioControllerTestBase {

    /**
     * Batch validate backward compatibility of all Realtime ASR historical requests
     */
    @Test
    public void testAllRealtimeAsrHistoricalRequestsBackwardCompatibility() {
        System.out.println("=== Starting batch Realtime ASR compatibility validation ===");

        // Load test data
        List<RealtimeAsrHistoricalDataLoader.RealtimeAsrTestCase> allCases;
        try {
            allCases = RealtimeAsrHistoricalDataLoader.loadRealtimeAsrRequests();
        } catch (Exception e) {
            System.err.println("Failed to load Realtime ASR test data: " + e.getMessage());
            e.printStackTrace();
            fail("Failed to load Realtime ASR test data: " + e.getMessage());
            return;
        }

        if (allCases.isEmpty()) {
            System.out.println("No Realtime ASR test data found, skipping Realtime ASR compatibility validation");
            return;
        }

        int totalCases = allCases.size();
        int passedCases = 0;
        List<String> failedCases = new ArrayList<>();

        System.out.println("Loaded Realtime ASR historical request scenarios: " + totalCases);

        for (RealtimeAsrHistoricalDataLoader.RealtimeAsrTestCase testCase : allCases) {
            try {
                System.out.println("--- Validating scenario: " + testCase.getScenarioName() + " ---");
                System.out.println("Scenario description: " + testCase.getDescription());

                // Execute single historical request test
                validateSingleRealtimeAsrHistoricalRequest(testCase);

                passedCases++;
                System.out.println("✅ " + testCase.getScenarioName() + " - Compatibility validation passed");

            } catch (Exception | AssertionError e) {
                failedCases.add(testCase.getScenarioName() + ": " + e.getMessage());
                System.err.println("❌ " + testCase.getScenarioName() + " - Compatibility validation failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        printTestSummary("Realtime ASR", totalCases, passedCases, failedCases);
    }

    /**
     * Validate single Realtime ASR historical request scenario logic
     */
    private void validateSingleRealtimeAsrHistoricalRequest(RealtimeAsrHistoricalDataLoader.RealtimeAsrTestCase testCase) throws Exception {
        // 1. Setup request context
        setupRealtimeAsrRequestContext(testCase);

        // 2. Prepare test environment
        setupMockForRealtimeAsrTestCase(testCase);

        // 3. Execute Controller core logic (bypass all AOP)
        // For WebSocket test, we validate the setup rather than actual connection
        try {
            if (testCase.getRequest().isWebsocketUpgrade()) {
                // Mock WebSocket upgrade headers
                when(mockHttpRequest.getHeader("Upgrade")).thenReturn("websocket");

                audioController.asrStream(
                    testCase.getRequest().getModel(),
                    mockHttpRequest,
                    mockHttpResponse
                );
            } else {
                // Test non-WebSocket request should return 400
                when(mockHttpRequest.getHeader("Upgrade")).thenReturn("http/1.1");

                audioController.asrStream(
                    testCase.getRequest().getModel(),
                    mockHttpRequest,
                    mockHttpResponse
                );
            }

            // 4. Validate response format compatibility
            validateRealtimeAsrResponseCompatibility(testCase);

            // 5. Validate underlying service call parameters
            validateRealtimeAsrServiceCallParameters(testCase);

        } catch (Exception e) {
            // For non-WebSocket requests, we expect certain exceptions
            if (!testCase.getRequest().isWebsocketUpgrade()) {
                // This is expected behavior
                System.out.println("Expected exception for non-WebSocket request: " + e.getMessage());
            } else {
                throw e;
            }
        }

        // 6. Reset Mock state for next test
        reset(channelRouter, adaptorManager, mockHttpRequest, mockHttpResponse);
    }

    /**
     * Setup Mock for Realtime ASR test scenarios
     */
    private void setupMockForRealtimeAsrTestCase(RealtimeAsrHistoricalDataLoader.RealtimeAsrTestCase testCase) {
        // Setup ChannelRouter Mock
        when(channelRouter.route(eq(testCase.getRequest().getEndpoint()), eq(testCase.getRequest().getModel()), any(), eq(false)))
            .thenReturn(testCase.getMockChannel());

        // Setup AdaptorManager Mock only for successful WebSocket cases
        if (testCase.getRequest().isWebsocketUpgrade()) {
            RealTimeAdaptor<AsrProperty> mockRealTimeAdaptor = mock(RealTimeAdaptor.class);
            when(mockRealTimeAdaptor.getPropertyClass()).thenReturn((Class) AsrProperty.class);
            when(adaptorManager.getProtocolAdaptor(eq(testCase.getRequest().getEndpoint()),
                                                  eq(testCase.getMockChannel().getProtocol()),
                                                  eq(RealTimeAdaptor.class)))
                .thenReturn(mockRealTimeAdaptor);
        }

        // Setup HTTP response mock for error cases
        if (!testCase.getRequest().isWebsocketUpgrade()) {
            doNothing().when(mockHttpResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        // Setup WebSocket request mock
        setupWebSocketRequestMock(testCase.getRequest().getEndpoint());

        // Ensure the mock channel has proper channelInfo for Jackson deserialization
        if (testCase.getMockChannel().getChannelInfo() == null) {
            testCase.getMockChannel().setChannelInfo("{\"apiKey\":\"test-key\",\"model\":\"" + testCase.getRequest().getModel() + "\"}");
        }
    }

    /**
     * Validate Realtime ASR response format compatibility
     */
    private void validateRealtimeAsrResponseCompatibility(RealtimeAsrHistoricalDataLoader.RealtimeAsrTestCase testCase) {
        if (testCase.getRequest().isWebsocketUpgrade()) {
            // For successful WebSocket requests, verify channel routing was called
            verify(channelRouter, times(1)).route(
                eq(testCase.getRequest().getEndpoint()),
                eq(testCase.getRequest().getModel()),
                any(),
                eq(false)
            );
        } else {
            // For non-WebSocket requests, verify 400 status was set
            verify(mockHttpResponse, times(1)).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        // Execute scenario-specific validation
        if (testCase.getCustomValidator() != null) {
            testCase.getCustomValidator().accept(testCase.getExpectedResponse());
        }
    }

    /**
     * Validate correctness of Realtime ASR underlying service call parameters
     */
    private void validateRealtimeAsrServiceCallParameters(RealtimeAsrHistoricalDataLoader.RealtimeAsrTestCase testCase) {
        if (testCase.getRequest().isWebsocketUpgrade()) {
            // Validate ChannelRouter call for WebSocket requests
            verify(channelRouter, times(1)).route(
                eq(testCase.getRequest().getEndpoint()),
                eq(testCase.getRequest().getModel()),
                any(), // API key
                eq(false) // Non-Mock mode
            );

            // Validate AdaptorManager call for WebSocket requests
            verify(adaptorManager, times(1)).getProtocolAdaptor(
                eq(testCase.getRequest().getEndpoint()),
                eq(testCase.getMockChannel().getProtocol()),
                eq(RealTimeAdaptor.class)
            );
        }

        // Validate request parameter validation passed
        assertTrue("Parameter validation failed for scenario: " + testCase.getScenarioName(),
                  testCase.getParameterValidator().test(testCase.getRequest()));
    }

    /**
     * Setup request context for Realtime ASR tests
     */
    private void setupRealtimeAsrRequestContext(RealtimeAsrHistoricalDataLoader.RealtimeAsrTestCase testCase) {
        setupRequestContext(testCase.getRequest().getEndpoint());
    }
}