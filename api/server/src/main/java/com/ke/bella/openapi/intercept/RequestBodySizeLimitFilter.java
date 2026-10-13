package com.ke.bella.openapi.intercept;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${bella.request.max-body-size:209715200}")
    private long maxBodySize;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodySize) {
            log.warn("Request body too large: {} bytes, limit: {} bytes, uri: {}", contentLength, maxBodySize, request.getRequestURI());
            rejectRequest(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void rejectRequest(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OpenapiResponse.OpenapiError error = new OpenapiResponse.OpenapiError(
                "Payload Too Large",
                "Request body exceeds the maximum allowed size (" + (maxBodySize / 1024 / 1024) + "MB)",
                HttpStatus.PAYLOAD_TOO_LARGE.value()
        );
        OpenapiResponse openapiResponse = OpenapiResponse.errorResponse(error);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(openapiResponse));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return true;
        }
        return false;
    }
}
