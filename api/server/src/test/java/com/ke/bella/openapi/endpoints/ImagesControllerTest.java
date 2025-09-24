package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesEditRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.images.generator.ImagesGeneratorAdaptor;
import com.ke.bella.openapi.protocol.images.editor.ImagesEditorAdaptor;
import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesEditorProperty;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.endpoints.testdata.ImagesHistoricalDataLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ImagesController compatibility tests
 *
 * Bypasses Spring AOP, interceptors, filters to directly test Controller core business logic
 * Separates test data from test logic, managing historical request cases via external JSON files
 *
 * Core objective: Ensure historical API requests remain unaffected during code iterations
 */
@RunWith(MockitoJUnitRunner.class)
public class ImagesControllerTest {

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private AdaptorManager adaptorManager;

    @Mock
    private LimiterManager limiterManager;

    @Mock
    private ImagesGeneratorAdaptor<ImagesProperty> mockGeneratorAdaptor;

    @Mock
    private ImagesEditorAdaptor<ImagesEditorProperty> mockEditorAdaptor;

    @Mock
    private ContentCachingRequestWrapper mockWrappedRequest;

    @InjectMocks
    private ImagesController imagesController;

    @Before
    public void setUp() {
        setupBasicMockEnvironment();
    }

    /**
     * Batch validate backward compatibility of all image generation historical requests
     */
    @Test
    public void testAllGenerationsHistoricalRequestsBackwardCompatibility() {
        System.out.println("=== Starting batch image generation compatibility validation ===");

        // Load test data
        List<ImagesHistoricalDataLoader.GenerationsTestCase> allCases =
            ImagesHistoricalDataLoader.loadGenerationsRequests();

        int totalCases = allCases.size();
        int passedCases = 0;
        List<String> failedCases = new ArrayList<>();

        System.out.println("Loaded image generation historical request scenarios: " + totalCases);

        for (ImagesHistoricalDataLoader.GenerationsTestCase testCase : allCases) {
            try {
                System.out.println("--- Validating scenario: " + testCase.getScenarioName() + " ---");
                System.out.println("Scenario description: " + testCase.getDescription());

                // Execute single historical request test
                validateSingleGenerationsHistoricalRequest(testCase);

                passedCases++;
                System.out.println("✅ " + testCase.getScenarioName() + " - Compatibility validation passed");

            } catch (Exception | AssertionError e) {
                failedCases.add(testCase.getScenarioName() + ": " + e.getMessage());
                System.err.println("❌ " + testCase.getScenarioName() + " - Compatibility validation failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("=== Batch image generation compatibility validation results ===");
        System.out.println("Total test scenarios: " + totalCases);
        System.out.println("Passed scenarios: " + passedCases);
        System.out.println("Failed scenarios: " + failedCases.size());

        if (!failedCases.isEmpty()) {
            System.err.println("Failed scenario details:");
            failedCases.forEach(failure -> System.err.println("  - " + failure));
            fail("Found " + failedCases.size() + " image generation historical request compatibility validation failures");
        }

        System.out.println("🎉 All image generation historical request compatibility validations completed!");
    }

    /**
     * Batch validate backward compatibility of all image editing historical requests
     */
    @Test
    public void testAllEditsHistoricalRequestsBackwardCompatibility() {
        System.out.println("=== Starting batch image editing compatibility validation ===");

        // Load test data
        List<ImagesHistoricalDataLoader.EditsTestCase> allCases =
            ImagesHistoricalDataLoader.loadEditsRequests();

        int totalCases = allCases.size();
        int passedCases = 0;
        List<String> failedCases = new ArrayList<>();

        System.out.println("Loaded image editing historical request scenarios: " + totalCases);

        for (ImagesHistoricalDataLoader.EditsTestCase testCase : allCases) {
            try {
                System.out.println("--- Validating scenario: " + testCase.getScenarioName() + " ---");
                System.out.println("Scenario description: " + testCase.getDescription());

                // Execute single historical request test
                validateSingleEditsHistoricalRequest(testCase);

                passedCases++;
                System.out.println("✅ " + testCase.getScenarioName() + " - Compatibility validation passed");

            } catch (Exception | AssertionError e) {
                failedCases.add(testCase.getScenarioName() + ": " + e.getMessage());
                System.err.println("❌ " + testCase.getScenarioName() + " - Compatibility validation failed: " + e.getMessage());
                e.printStackTrace(); // Print complete stack trace
            }
        }

        // Output final results
        System.out.println("=== Batch image editing compatibility validation results ===");
        System.out.println("Total test scenarios: " + totalCases);
        System.out.println("Passed scenarios: " + passedCases);
        System.out.println("Failed scenarios: " + failedCases.size());

        if (!failedCases.isEmpty()) {
            System.err.println("Failed scenario details:");
            failedCases.forEach(failure -> System.err.println("  - " + failure));
            fail("Found " + failedCases.size() + " image editing historical request compatibility validation failures");
        }

        System.out.println("🎉 All image editing historical request compatibility validations completed!");
    }

    /**
     * Validate single image generation historical request scenario logic
     */
    private void validateSingleGenerationsHistoricalRequest(ImagesHistoricalDataLoader.GenerationsTestCase testCase) {
        // 1. Setup request context
        setupGenerationsRequestContext();

        // 2. Prepare test environment
        setupMockForGenerationsTestCase(testCase);

        // 3. Execute Controller core logic (bypass all AOP)
        ImagesResponse actualResponse = imagesController.generateImages(testCase.getRequest());

        // 4. Validate response format compatibility
        validateGenerationsResponseCompatibility(testCase, actualResponse);

        // 5. Validate underlying service call parameters
        validateGenerationsServiceCallParameters(testCase);

        // 6. Reset Mock state for next test
        reset(channelRouter, adaptorManager, mockGeneratorAdaptor);
    }

    /**
     * Validate single image editing historical request scenario logic
     */
    private void validateSingleEditsHistoricalRequest(ImagesHistoricalDataLoader.EditsTestCase testCase) {
        // 1. Setup request context
        setupEditsRequestContext();

        // 2. Prepare test environment
        setupMockForEditsTestCase(testCase);

        // 3. Create mock multipart request for the new editImages signature
        MockMultipartHttpServletRequest mockMultipartRequest = createMockMultipartRequest(testCase.getRequest());

        // 4. Execute Controller core logic (bypass all AOP)
        ImagesResponse actualResponse = imagesController.editImages(mockMultipartRequest);

        // 5. Validate response format compatibility
        validateEditsResponseCompatibility(testCase, actualResponse);

        // 6. Validate underlying service call parameters
        validateEditsServiceCallParameters(testCase);

        // 7. Reset Mock state for next test
        reset(channelRouter, adaptorManager, mockEditorAdaptor);
    }

    /**
     * Setup Mock for image generation test scenarios
     */
    private void setupMockForGenerationsTestCase(ImagesHistoricalDataLoader.GenerationsTestCase testCase) {
        // Setup ChannelRouter Mock
        when(channelRouter.route(eq("/v1/images/generations"), eq(testCase.getRequest().getModel()), any(), eq(false)))
            .thenReturn(testCase.getMockChannel());

        // Setup AdaptorManager Mock
        when(adaptorManager.getProtocolAdaptor(eq("/v1/images/generations"),
                                              eq(testCase.getMockChannel().getProtocol()),
                                              eq(ImagesGeneratorAdaptor.class)))
            .thenReturn(mockGeneratorAdaptor);

        when(mockGeneratorAdaptor.getPropertyClass())
            .thenReturn((Class) ImagesProperty.class);

        // Setup Adaptor Mock response
        when(mockGeneratorAdaptor.generateImages(any(ImagesRequest.class),
                                               eq(testCase.getMockChannel().getUrl()),
                                               any(ImagesProperty.class)))
            .thenReturn(testCase.getExpectedResponse());
    }

    /**
     * Setup Mock for image editing test scenarios
     */
    private void setupMockForEditsTestCase(ImagesHistoricalDataLoader.EditsTestCase testCase) {
        // Setup ChannelRouter Mock
        when(channelRouter.route(eq("/v1/images/edits"), eq(testCase.getRequest().getModel()), any(), eq(false)))
            .thenReturn(testCase.getMockChannel());

        // Setup AdaptorManager Mock
        when(adaptorManager.getProtocolAdaptor(eq("/v1/images/edits"),
                                              eq(testCase.getMockChannel().getProtocol()),
                                              eq(ImagesEditorAdaptor.class)))
            .thenReturn(mockEditorAdaptor);

        when(mockEditorAdaptor.getPropertyClass())
            .thenReturn((Class) ImagesEditorProperty.class);

        // Setup Adaptor Mock response
        when(mockEditorAdaptor.editImages(any(ImagesEditRequest.class),
                                        eq(testCase.getMockChannel().getUrl()),
                                        any(ImagesEditorProperty.class)))
            .thenReturn(testCase.getExpectedResponse());
    }

    /**
     * Validate image generation response format compatibility
     */
    private void validateGenerationsResponseCompatibility(ImagesHistoricalDataLoader.GenerationsTestCase testCase,
                                                        ImagesResponse actualResponse) {
        assertNotNull(testCase.getScenarioName() + " - Response cannot be null", actualResponse);
        assertNotNull(testCase.getScenarioName() + " - Response must contain data field", actualResponse.getData());
        assertFalse(testCase.getScenarioName() + " - Response data cannot be empty", actualResponse.getData().isEmpty());


        // Validate required fields for each image data
        for (int i = 0; i < actualResponse.getData().size(); i++) {
            ImagesResponse.ImageData imageData = actualResponse.getData().get(i);
            String fieldPrefix = testCase.getScenarioName() + " - Image" + (i + 1);

            // Validate corresponding fields based on response_format
            if ("url".equals(testCase.getRequest().getResponse_format())) {
                assertNotNull(fieldPrefix + " - URL cannot be null", imageData.getUrl());
            } else if ("b64_json".equals(testCase.getRequest().getResponse_format())) {
                assertNotNull(fieldPrefix + " - Base64 data cannot be null", imageData.getB64_json());
            } else {
				assertTrue(fieldPrefix + " - Base64 data or URL cannot be null",
					imageData.getB64_json() != null || imageData.getUrl() != null);
			}


            // Validate size field
            if (testCase.getRequest().getSize() != null) {
                assertEquals(fieldPrefix + " - Size must match request",
                           testCase.getRequest().getSize(),
                           imageData.getSize());
            }
        }

        // Execute scenario-specific validation
        if (testCase.getCustomValidator() != null) {
            testCase.getCustomValidator().accept(actualResponse);
        }
    }

    /**
     * Validate image editing response format compatibility
     */
    private void validateEditsResponseCompatibility(ImagesHistoricalDataLoader.EditsTestCase testCase,
                                                  ImagesResponse actualResponse) {
        assertNotNull(testCase.getScenarioName() + " - Response cannot be null", actualResponse);
        assertNotNull(testCase.getScenarioName() + " - Response must contain data field", actualResponse.getData());
        assertFalse(testCase.getScenarioName() + " - Response data cannot be empty", actualResponse.getData().isEmpty());


        // Validate required fields for each image data
        for (int i = 0; i < actualResponse.getData().size(); i++) {
            ImagesResponse.ImageData imageData = actualResponse.getData().get(i);
            String fieldPrefix = testCase.getScenarioName() + " - Image" + (i + 1);

            // Validate corresponding fields based on response_format
            if ("url".equals(testCase.getRequest().getResponse_format())) {
                assertNotNull(fieldPrefix + " - URL cannot be null", imageData.getUrl());
            } else if ("b64_json".equals(testCase.getRequest().getResponse_format())) {
                assertNotNull(fieldPrefix + " - Base64 data cannot be null", imageData.getB64_json());
            } else {
				assertTrue(fieldPrefix + " - Base64 data or URL cannot be null",
					imageData.getB64_json() != null || imageData.getUrl() != null);
			}

            // Validate size field
            if (testCase.getRequest().getSize() != null) {
                assertEquals(fieldPrefix + " - Size must match request",
                           testCase.getRequest().getSize(),
                           imageData.getSize());
            }
        }

        // Execute scenario-specific validation
        if (testCase.getCustomValidator() != null) {
            testCase.getCustomValidator().accept(actualResponse);
        }
    }

    /**
     * Validate correctness of image generation underlying service call parameters
     */
    private void validateGenerationsServiceCallParameters(ImagesHistoricalDataLoader.GenerationsTestCase testCase) {
        // Validate ChannelRouter call
        verify(channelRouter, times(1)).route(
            eq("/v1/images/generations"),
            eq(testCase.getRequest().getModel()),
            any(), // API key
            eq(false) // Non-Mock mode
        );

        // Validate AdaptorManager call
        verify(adaptorManager, times(1)).getProtocolAdaptor(
            eq("/v1/images/generations"),
            eq(testCase.getMockChannel().getProtocol()),
            eq(ImagesGeneratorAdaptor.class)
        );

        // Validate Adaptor call parameter passing
        verify(mockGeneratorAdaptor, times(1)).generateImages(
            argThat(req -> testCase.getParameterValidator().test(req)),
            eq(testCase.getMockChannel().getUrl()),
            any(ImagesProperty.class)
        );
    }

    /**
     * Validate correctness of image editing underlying service call parameters
     */
    private void validateEditsServiceCallParameters(ImagesHistoricalDataLoader.EditsTestCase testCase) {
        // Validate ChannelRouter call
        verify(channelRouter, times(1)).route(
            eq("/v1/images/edits"),
            eq(testCase.getRequest().getModel()),
            any(), // API key
            eq(false) // Non-Mock mode
        );

        // Validate AdaptorManager call
        verify(adaptorManager, times(1)).getProtocolAdaptor(
            eq("/v1/images/edits"),
            eq(testCase.getMockChannel().getProtocol()),
            eq(ImagesEditorAdaptor.class)
        );

        // Validate Adaptor call parameter passing
        verify(mockEditorAdaptor, times(1)).editImages(
            argThat(req -> testCase.getParameterValidator().test(req)),
            eq(testCase.getMockChannel().getUrl()),
            any(ImagesEditorProperty.class)
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
     * Setup request context for image generation tests
     */
    private void setupGenerationsRequestContext() {
        when(mockWrappedRequest.getRequestURI()).thenReturn("/v1/images/generations");
        EndpointContext.setRequest(mockWrappedRequest);
    }

    /**
     * Setup request context for image editing tests
     */
    private void setupEditsRequestContext() {
        when(mockWrappedRequest.getRequestURI()).thenReturn("/v1/images/edits");
        EndpointContext.setRequest(mockWrappedRequest);
    }

    /**
     * Create mock multipart request from ImagesEditRequest for testing
     */
    private MockMultipartHttpServletRequest createMockMultipartRequest(ImagesEditRequest editRequest) {
        MockMultipartHttpServletRequest mockRequest = new MockMultipartHttpServletRequest();
        mockRequest.setRequestURI("/v1/images/edits");
        mockRequest.setMethod("POST");
        mockRequest.setContentType("multipart/form-data");

        // Add basic parameters
        if (editRequest.getModel() != null) {
            mockRequest.setParameter("model", editRequest.getModel());
        }
        if (editRequest.getPrompt() != null) {
            mockRequest.setParameter("prompt", editRequest.getPrompt());
        }
        if (editRequest.getSize() != null) {
            mockRequest.setParameter("size", editRequest.getSize());
        }
        if (editRequest.getResponse_format() != null) {
            mockRequest.setParameter("response_format", editRequest.getResponse_format());
        }
        if (editRequest.getUser() != null) {
            mockRequest.setParameter("user", editRequest.getUser());
        }
        if (editRequest.getN() != null) {
            mockRequest.setParameter("n", String.valueOf(editRequest.getN()));
        }

        // Add image data as multipart files or parameters
        // Use compatible parameter names that match backend processing logic
        if (editRequest.getImage_b64_json() != null) {
            String[] base64Array = editRequest.getImage_b64_json();
            if (base64Array.length == 1) {
                // Single value: use "image_b64_json"
                mockRequest.setParameter("image_b64_json", base64Array[0]);
            } else {
                // Multiple values: use "image_b64_json[]"
                for (String base64Value : base64Array) {
                    mockRequest.addParameter("image_b64_json[]", base64Value);
                }
            }
        }

        if (editRequest.getImage_url() != null) {
            String[] urlArray = editRequest.getImage_url();
            if (urlArray.length == 1) {
                // Single value: use "image_url"
                mockRequest.setParameter("image_url", urlArray[0]);
            } else {
                // Multiple values: use "image_url[]"
                for (String urlValue : urlArray) {
                    mockRequest.addParameter("image_url[]", urlValue);
                }
            }
        }

        // Add mock image file if needed (for testing purposes)
        if (editRequest.getImage() != null || editRequest.getImage_b64_json() != null || editRequest.getImage_url() != null) {
            MockMultipartFile mockImageFile = new MockMultipartFile(
                "image",
                "test-image.png",
                "image/png",
                "fake-image-data".getBytes()
            );
            mockRequest.addFile(mockImageFile);
        }

        return mockRequest;
    }

    @After
    public void tearDown() {
        BellaContext.clearAll();
        EndpointContext.clearAll();
    }
}
