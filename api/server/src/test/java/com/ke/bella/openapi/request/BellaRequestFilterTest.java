package com.ke.bella.openapi.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BellaRequestFilterTest {
    private static final String SERVICE_ID = "test-service";

    @Before
    public void setUp() {
        EndpointContext.clearAll();
    }

    @After
    public void clearContext() {
        EndpointContext.clearAll();
    }

    @Test
    public void capturesExternalRequestIdAndKeepsInternalRequestIdIndependent() {
        HttpServletRequest request = requestWithExternalRequestId("caller-request-id");
        HttpServletResponse response = mock(HttpServletResponse.class);

        new TestBellaRequestFilter().apply(request, response);
        EndpointProcessData processData = EndpointContext.getProcessData();

        assertThat(processData.getExternalRequestId()).isEqualTo("caller-request-id");
        assertThat(processData.getRequestId()).isNotBlank();
        assertThat(processData.getRequestId()).isNotEqualTo("caller-request-id");

        JsonNode log = JacksonUtils.deserialize(JacksonUtils.serialize(processData));
        assertThat(log.get("externalRequestId").asText()).isEqualTo("caller-request-id");
        assertThat(log.get("requestId").asText()).isEqualTo(processData.getRequestId());
        verify(response).addHeader(BellaContext.BELLA_REQUEST_ID_HEADER, processData.getRequestId());
    }

    @Test
    public void leavesExternalRequestIdEmptyWhenHeaderIsMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(Collections.emptyList()));
        when(request.getRequestURI()).thenReturn("/");

        new TestBellaRequestFilter().apply(request, response);
        EndpointProcessData processData = EndpointContext.getProcessData();

        assertThat(processData.getExternalRequestId()).isNull();
        assertThat(processData.getRequestId()).isNotBlank();
        verify(response).addHeader(BellaContext.BELLA_REQUEST_ID_HEADER, processData.getRequestId());
    }

    private HttpServletRequest requestWithExternalRequestId(String requestId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames())
                .thenReturn(Collections.enumeration(Collections.singleton(BellaContext.EXTERNAL_REQUEST_ID_HEADER)));
        when(request.getHeader(BellaContext.EXTERNAL_REQUEST_ID_HEADER)).thenReturn(requestId);
        when(request.getRequestURI()).thenReturn("/");
        return request;
    }

    private static class TestBellaRequestFilter extends BellaRequestFilter {
        private TestBellaRequestFilter() {
            super(SERVICE_ID);
        }

        private void apply(HttpServletRequest request, HttpServletResponse response) {
            bellaRequestFilter(request, response);
        }
    }
}
