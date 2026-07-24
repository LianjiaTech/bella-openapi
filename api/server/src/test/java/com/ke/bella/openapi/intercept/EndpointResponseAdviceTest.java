package com.ke.bella.openapi.intercept;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.openapi.protocol.OpenapiResponse;

class EndpointResponseAdviceTest {

    @Test
    void applyErrorStatusKeepsStandardHttpStatusBehavior() {
        OpenapiResponse openapiResponse = errorResponse(429);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        assertDoesNotThrow(() -> applyErrorStatus(openapiResponse, servletResponse));

        assertEquals(429, servletResponse.getStatus());
        assertEquals(429, openapiResponse.getError().getHttpCode());
    }

    @Test
    void applyErrorStatusSetsRawServletStatusForNonStandardHttpCode() {
        OpenapiResponse openapiResponse = errorResponse(433);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        assertDoesNotThrow(() -> applyErrorStatus(openapiResponse, servletResponse));

        assertEquals(433, servletResponse.getStatus());
        assertEquals(433, openapiResponse.getError().getHttpCode());
    }

    @Test
    void applyErrorStatusFallsBackToInternalServerErrorForMissingHttpCode() {
        OpenapiResponse openapiResponse = errorResponse(null);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        assertDoesNotThrow(() -> applyErrorStatus(openapiResponse, servletResponse));

        assertEquals(500, servletResponse.getStatus());
        assertNull(openapiResponse.getError().getHttpCode());
    }

    @Test
    void applyErrorStatusFallsBackToBadGatewayForInvalidLowHttpCode() {
        OpenapiResponse openapiResponse = errorResponse(99);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        assertDoesNotThrow(() -> applyErrorStatus(openapiResponse, servletResponse));

        assertEquals(502, servletResponse.getStatus());
        assertEquals(99, openapiResponse.getError().getHttpCode());
    }

    @Test
    void applyErrorStatusFallsBackToBadGatewayForInvalidHighHttpCode() {
        OpenapiResponse openapiResponse = errorResponse(600);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        assertDoesNotThrow(() -> applyErrorStatus(openapiResponse, servletResponse));

        assertEquals(502, servletResponse.getStatus());
        assertEquals(600, openapiResponse.getError().getHttpCode());
    }

    private void applyErrorStatus(OpenapiResponse openapiResponse, MockHttpServletResponse servletResponse) {
        EndpointResponseAdvice advice = new EndpointResponseAdvice();
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        ReflectionTestUtils.invokeMethod(advice, "applyErrorStatus", openapiResponse, response);
    }

    private OpenapiResponse errorResponse(Integer httpCode) {
        OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                .httpCode(httpCode)
                .message("upstream error")
                .build();
        return OpenapiResponse.errorResponse(error);
    }
}
