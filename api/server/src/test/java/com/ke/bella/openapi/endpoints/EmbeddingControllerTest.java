package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.embedding.EmbeddingRequest;
import com.ke.bella.openapi.protocol.embedding.EmbeddingResponse;
import com.ke.bella.openapi.protocol.embedding.EmbeddingAdaptor;
import com.ke.bella.openapi.protocol.embedding.EmbeddingProperty;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.endpoints.testdata.EmbeddingHistoricalDataLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EmbeddingController compatibility tests
 *
 * Bypasses Spring AOP, interceptors, filters to directly test Controller core business logic
 * Separates test data from test logic, managing historical request cases via external JSON files
 *
 * Core objective: Ensure historical API requests remain unaffected during code iterations
 */
@RunWith(MockitoJUnitRunner.class)
public class EmbeddingControllerTest {

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private AdaptorManager adaptorManager;

    @Mock
    private LimiterManager limiterManager;

    @Mock
    private EmbeddingAdaptor<EmbeddingProperty> mockEmbeddingAdaptor;

    @Mock
    private ContentCachingRequestWrapper mockWrappedRequest;

    @InjectMocks
    private EmbeddingController embeddingController;

    @Before
    public void setUp() {
        setupBasicMockEnvironment();
    }

    /**
     * Batch validate backward compatibility of all embedding historical requests
     */
    @Test
    public void testAllEmbeddingHistoricalRequestsBackwardCompatibility() {
        System.out.println("=== Starting batch embedding compatibility validation ===");

        // Load test data
        List<EmbeddingHistoricalDataLoader.EmbeddingTestCase> allCases =
            EmbeddingHistoricalDataLoader.loadEmbeddingRequests();

        int totalCases = allCases.size();
        int passedCases = 0;
        List<String> failedCases = new ArrayList<>();

        System.out.println("Loaded embedding historical request scenarios: " + totalCases);

        for (EmbeddingHistoricalDataLoader.EmbeddingTestCase testCase : allCases) {
            try {
                System.out.println("--- Validating scenario: " + testCase.getScenarioName() + " ---");
                System.out.println("Scenario description: " + testCase.getDescription());

                // Execute single historical request test
                validateSingleEmbeddingHistoricalRequest(testCase);

                passedCases++;
                System.out.println("✅ " + testCase.getScenarioName() + " - Compatibility validation passed");

            } catch (Exception | AssertionError e) {
                failedCases.add(testCase.getScenarioName() + ": " + e.getMessage());
                System.err.println("❌ " + testCase.getScenarioName() + " - Compatibility validation failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("=== Batch embedding compatibility validation results ===");
        System.out.println("Total test scenarios: " + totalCases);
        System.out.println("Passed scenarios: " + passedCases);
        System.out.println("Failed scenarios: " + failedCases.size());

        if (!failedCases.isEmpty()) {
            System.err.println("Failed scenario details:");
            failedCases.forEach(failure -> System.err.println("  - " + failure));
            fail("Found " + failedCases.size() + " embedding historical request compatibility validation failures");
        }

        System.out.println("🎉 All embedding historical request compatibility validations completed!");
    }

    /**
     * Validate single embedding historical request scenario logic
     */
    private void validateSingleEmbeddingHistoricalRequest(EmbeddingHistoricalDataLoader.EmbeddingTestCase testCase) {
        // 1. Setup request context
        setupEmbeddingRequestContext();

        // 2. Prepare test environment
        setupMockForEmbeddingTestCase(testCase);

        // 3. Execute Controller core logic (bypass all AOP)
        Object actualResponse = embeddingController.embedding(testCase.getRequest());

        // 4. Validate response format compatibility
        validateEmbeddingResponseCompatibility(testCase, (EmbeddingResponse) actualResponse);

        // 5. Validate underlying service call parameters
        validateEmbeddingServiceCallParameters(testCase);

        // 6. Reset Mock state for next test
        reset(channelRouter, adaptorManager, mockEmbeddingAdaptor);
    }

    /**
     * Setup Mock for embedding test scenarios
     */
    private void setupMockForEmbeddingTestCase(EmbeddingHistoricalDataLoader.EmbeddingTestCase testCase) {
        // Setup ChannelRouter Mock
        when(channelRouter.route(eq("/v1/embeddings"), eq(testCase.getRequest().getModel()), any(), eq(false)))
            .thenReturn(testCase.getMockChannel());

        // Setup AdaptorManager Mock
        when(adaptorManager.getProtocolAdaptor(eq("/v1/embeddings"),
                                              eq(testCase.getMockChannel().getProtocol()),
                                              eq(EmbeddingAdaptor.class)))
            .thenReturn(mockEmbeddingAdaptor);

        when(mockEmbeddingAdaptor.getPropertyClass())
            .thenReturn((Class) EmbeddingProperty.class);

        // Setup Adaptor Mock response
        when(mockEmbeddingAdaptor.embedding(any(EmbeddingRequest.class),
                                          eq(testCase.getMockChannel().getUrl()),
                                          any(EmbeddingProperty.class)))
            .thenReturn(testCase.getExpectedResponse());
    }

    /**
     * Validate embedding response format compatibility
     */
    private void validateEmbeddingResponseCompatibility(EmbeddingHistoricalDataLoader.EmbeddingTestCase testCase,
                                                       EmbeddingResponse actualResponse) {
        assertNotNull(testCase.getScenarioName() + " - Response cannot be null", actualResponse);
        assertNotNull(testCase.getScenarioName() + " - Response must contain data field", actualResponse.getData());
        assertFalse(testCase.getScenarioName() + " - Response data cannot be empty", actualResponse.getData().isEmpty());

        // Validate required fields for each embedding data
        for (int i = 0; i < actualResponse.getData().size(); i++) {
            EmbeddingResponse.EmbeddingData embeddingData = actualResponse.getData().get(i);
            String fieldPrefix = testCase.getScenarioName() + " - Embedding" + (i + 1);

            // Validate embedding field
            assertNotNull(fieldPrefix + " - Embedding cannot be null", embeddingData.getEmbedding());

            // Validate object field
            assertNotNull(fieldPrefix + " - Object field cannot be null", embeddingData.getObject());
            assertEquals(fieldPrefix + " - Object field must be 'embedding'", "embedding", embeddingData.getObject());

            // Validate index field
            assertEquals(fieldPrefix + " - Index must match position", i, embeddingData.getIndex());
        }

        // Validate model field
        if (testCase.getRequest().getModel() != null) {
            assertEquals(testCase.getScenarioName() + " - Model must match request",
                       testCase.getRequest().getModel(),
                       actualResponse.getModel());
        }

        // Validate object field
        assertEquals(testCase.getScenarioName() + " - Object field must be 'list'", "list", actualResponse.getObject());

        // Validate usage field if present
        if (actualResponse.getUsage() != null) {
            assertTrue(testCase.getScenarioName() + " - Usage prompt_tokens must be >= 0",
                      actualResponse.getUsage().getPrompt_tokens() >= 0);
            assertTrue(testCase.getScenarioName() + " - Usage total_tokens must be >= 0",
                      actualResponse.getUsage().getTotal_tokens() >= 0);
        }

        // Execute scenario-specific validation
        if (testCase.getCustomValidator() != null) {
            testCase.getCustomValidator().accept(actualResponse);
        }
    }

    /**
     * Validate correctness of embedding underlying service call parameters
     */
    private void validateEmbeddingServiceCallParameters(EmbeddingHistoricalDataLoader.EmbeddingTestCase testCase) {
        // Validate ChannelRouter call
        verify(channelRouter, times(1)).route(
            eq("/v1/embeddings"),
            eq(testCase.getRequest().getModel()),
            any(), // API key
            eq(false) // Non-Mock mode
        );

        // Validate AdaptorManager call
        verify(adaptorManager, times(1)).getProtocolAdaptor(
            eq("/v1/embeddings"),
            eq(testCase.getMockChannel().getProtocol()),
            eq(EmbeddingAdaptor.class)
        );

        // Validate Adaptor call parameter passing
        verify(mockEmbeddingAdaptor, times(1)).embedding(
            argThat(req -> testCase.getParameterValidator().test(req)),
            eq(testCase.getMockChannel().getUrl()),
            any(EmbeddingProperty.class)
        );
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
     * Setup request context for embedding tests
     */
    private void setupEmbeddingRequestContext() {
        when(mockWrappedRequest.getRequestURI()).thenReturn("/v1/embeddings");
        EndpointContext.setRequest(mockWrappedRequest);
    }

    @After
    public void tearDown() {
        BellaContext.clearAll();
        EndpointContext.clearAll();
    }
}