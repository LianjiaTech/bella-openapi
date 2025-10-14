package com.ke.bella.openapi.endpoints;

import com.ke.bella.job.queue.JobQueueClient;
import com.ke.bella.job.queue.config.JobQueueProperties;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.service.EndpointDataService;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.util.ContentCachingRequestWrapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for ChatController tests providing common mock setup and utilities
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class ChatControllerTestBase {

    @Mock
    protected ChannelRouter channelRouter;

    @Mock
    protected AdaptorManager adaptorManager;

    @Mock
    protected LimiterManager limiterManager;

    @Mock
    protected EndpointLogger endpointLogger;

    @Mock
    protected ISafetyCheckService.IChatSafetyCheckService safetyCheckService;

    @Mock
    protected JobQueueProperties jobQueueProperties;

    @Mock
    protected ContentCachingRequestWrapper mockWrappedRequest;

    @Mock
    protected EndpointDataService endpointDataService;

    @InjectMocks
    protected ChatController chatController;

    protected MockedStatic<JobQueueClient> mockedJobQueueClient;

    @Before
    public void setUp() {
        setupBasicMockEnvironment();
        setupJobQueueClientMock();
        setupJobQueuePropertiesMock();
        setupChatControllerFields();
    }

    /**
     * Setup ChatController fields to avoid NPE
     */
    protected void setupChatControllerFields() {
        // Set maxModelsPerRequest field using reflection to avoid NPE
        try {
            java.lang.reflect.Field maxModelsField = ChatController.class.getDeclaredField("maxModelsPerRequest");
            maxModelsField.setAccessible(true);
            maxModelsField.set(chatController, 3);
        } catch (Exception e) {
            // Ignore reflection errors
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

    /**
     * Setup basic Mock environment
     */
    protected void setupBasicMockEnvironment() {
        // Setup API key in BellaContext
        ApikeyInfo testApikey = new ApikeyInfo();
        testApikey.setApikey("test-key");
        ApikeyInfo.RolePath rolePath = new ApikeyInfo.RolePath();
        rolePath.getIncluded().add("/v1/**");
        testApikey.setRolePath(rolePath);
        BellaContext.setApikey(testApikey);

        // Setup EndpointDataService mock behavior
        // These methods need to actually execute to set EndpointContext state
        lenient().doCallRealMethod().when(endpointDataService).setEndpointData(anyString(), anyString(), any());
        lenient().doCallRealMethod().when(endpointDataService).setChannel(any());
    }

    /**
     * Setup JobQueueClient static mock
     */
    protected void setupJobQueueClientMock() {
        mockedJobQueueClient = Mockito.mockStatic(JobQueueClient.class);
        mockedJobQueueClient.when(() -> JobQueueClient.getInstance(anyString()))
                            .thenReturn(mock(JobQueueClient.class));
    }

    /**
     * Setup JobQueueProperties mock
     */
    protected void setupJobQueuePropertiesMock() {
        lenient().when(jobQueueProperties.getUrl()).thenReturn("http://mock-queue-service");
    }

    /**
     * Setup common HTTP request context
     */
    protected void setupRequestContext(String requestURI) {
        when(mockWrappedRequest.getRequestURI()).thenReturn(requestURI);
        EndpointContext.setRequest(mockWrappedRequest);

        // Set up EndpointProcessData with the required apikey
        ApikeyInfo testApikey = BellaContext.getApikey();
        if (testApikey != null) {
            EndpointContext.getProcessData().setApikey(testApikey.getApikey());
        }
    }

    /**
     * Print test summary
     */
    protected void printTestSummary(String testType, int totalCases, int passedCases, java.util.List<String> failedCases) {
        System.out.println("=== " + testType + " compatibility validation results ===");
        System.out.println("Total test scenarios: " + totalCases);
        System.out.println("Passed scenarios: " + passedCases);
        System.out.println("Failed scenarios: " + failedCases.size());

        if (!failedCases.isEmpty()) {
            System.err.println("Failed scenario details:");
            failedCases.forEach(failure -> System.err.println("  - " + failure));
            throw new AssertionError("Found " + failedCases.size() + " " + testType + " historical request compatibility validation failures");
        }

        System.out.println("🎉 All " + testType + " historical request compatibility validations completed!");
    }
}
