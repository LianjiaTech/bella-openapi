package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.console.ConsoleContext;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Author: Stan Sai Date: 2024/8/13 16:50 description:
 */
@Component
public class OpenapiRequestFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String requestId = request.getHeader("X-BELLA-REQUEST-ID");
            if(StringUtils.isBlank(requestId)) {
                requestId = UUID.randomUUID().toString();
            }
            BellaContext.setRequestId(requestId);
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
            BellaContext.setRequest(wrappedRequest);
            chain.doFilter(wrappedRequest, response);
        } finally {
            ConsoleContext.clearAll();
            BellaContext.clearAll();
        }
    }
}
