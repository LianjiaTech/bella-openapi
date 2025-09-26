package com.ke.bella.openapi.endpoints.audio;

import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.endpoints.AudioController;
import com.ke.bella.job.queue.JobQueueClient;
import com.ke.bella.job.queue.config.JobQueueProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Base class for AudioController tests providing common mock setup and utilities
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class AudioControllerTestBase {

    @Mock
    protected ChannelRouter channelRouter;

    @Mock
    protected AdaptorManager adaptorManager;

    @Mock
    protected LimiterManager limiterManager;

    @Mock
    protected ContentCachingRequestWrapper mockWrappedRequest;

    @Mock
    protected HttpServletRequest mockHttpRequest;

    @Mock
    protected HttpServletResponse mockHttpResponse;

    @Mock
    protected ServletOutputStream mockOutputStream;

    @Mock
    protected AsyncContext mockAsyncContext;

    @Mock
    protected JobQueueClient mockJobQueueClient;

    @Mock
    protected JobQueueProperties mockJobQueueProperties;

    @InjectMocks
    protected AudioController audioController;

    protected MockedStatic<JobQueueClient> mockedJobQueueClient;

    @Before
    public void setUp() {
        setupBasicMockEnvironment();
        setupJobQueueClientMock();
        setupJobQueuePropertiesMock();
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
    }

    /**
     * Setup JobQueueClient static mock
     */
    protected void setupJobQueueClientMock() {
        mockedJobQueueClient = Mockito.mockStatic(JobQueueClient.class);
        mockedJobQueueClient.when(() -> JobQueueClient.getInstance(anyString()))
                            .thenReturn(mockJobQueueClient);
    }

    /**
     * Setup JobQueueProperties mock
     */
    protected void setupJobQueuePropertiesMock() {
		lenient().when(mockJobQueueProperties.getUrl()).thenReturn("http://mock-queue-service");
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
     * Setup common HTTP response mock
     */
    protected void setupHttpResponseMock() throws Exception {
        when(mockHttpResponse.getOutputStream()).thenReturn(mockOutputStream);
    }

    /**
     * Setup common HTTP request mock for async operations
     */
    protected void setupAsyncHttpRequestMock() {
        when(mockHttpRequest.startAsync()).thenReturn(mockAsyncContext);
        doNothing().when(mockAsyncContext).setTimeout(anyLong());
    }

    /**
     * Setup common WebSocket request mock
     */
    protected void setupWebSocketRequestMock(String endpoint) {
        // Setup HTTP request mock with necessary methods for ServletServerHttpRequest
        when(mockHttpRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080" + endpoint));
        when(mockHttpRequest.getRequestURI()).thenReturn(endpoint);
        when(mockHttpRequest.getContextPath()).thenReturn("");
        when(mockHttpRequest.getServletPath()).thenReturn("");
        when(mockHttpRequest.getPathInfo()).thenReturn(endpoint);
        when(mockHttpRequest.getQueryString()).thenReturn(null);
        when(mockHttpRequest.getScheme()).thenReturn("http");
        when(mockHttpRequest.getServerName()).thenReturn("localhost");
        when(mockHttpRequest.getServerPort()).thenReturn(8080);

        // Mock HTTP headers for WebSocket handshake
        when(mockHttpRequest.getHeaderNames()).thenReturn(Collections.enumeration(Arrays.asList("Upgrade", "Connection")));
        when(mockHttpRequest.getHeaders("Upgrade")).thenReturn(Collections.enumeration(Collections.singletonList("websocket")));
        when(mockHttpRequest.getHeaders("Connection")).thenReturn(Collections.enumeration(Collections.singletonList("Upgrade")));
        when(mockHttpRequest.getHeader("Sec-WebSocket-Key")).thenReturn("test-websocket-key");
        when(mockHttpRequest.getHeader("Sec-WebSocket-Version")).thenReturn("13");
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
