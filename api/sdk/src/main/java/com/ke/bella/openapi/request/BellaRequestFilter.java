package com.ke.bella.openapi.request;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.CredentialAuthenticator;
import com.ke.bella.openapi.auth.CredentialExtractor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;

public class BellaRequestFilter extends OncePerRequestFilter {
    private final String serviceId;
    private final CredentialAuthenticator authenticator;

    public BellaRequestFilter(String serviceId) {
        this(serviceId, null);
    }

    public BellaRequestFilter(String serviceId, CredentialAuthenticator authenticator) {
        Assert.isTrue(StringUtils.isNotEmpty(serviceId), "serviceId不能为空");
        this.serviceId = serviceId;
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            bellaRequestFilter(request, response);
            filterChain.doFilter(request, response);
        } finally {
            BellaContext.clearAll();
        }
    }

    protected void bellaRequestFilter(HttpServletRequest request, HttpServletResponse response) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement().toUpperCase();
            if(headerName.startsWith("X-BELLA-")) {
                BellaContext.getHeaders().put(headerName, request.getHeader(headerName));
            }
        }
        String externalRequestId = request.getHeader(BellaContext.EXTERNAL_REQUEST_ID_HEADER);
        if(externalRequestId != null) {
            BellaContext.getHeaders().put(BellaContext.EXTERNAL_REQUEST_ID_HEADER, externalRequestId);
        }

        String auth = CredentialExtractor.extract(request);
        if(auth != null && authenticator != null) {
            AuthResult authResult = authenticator.authenticate(auth, request.getRequestURI());
            if(authResult != null) {
                BellaContext.setAuthResult(authResult);
            }
        }
        String bellaTraceId = BellaContext.getTraceId();
        if(bellaTraceId == null) {
            bellaTraceId = BellaContext.generateTraceId(serviceId);
            BellaContext.getHeaders().put(BellaContext.BELLA_TRACE_HEADER, bellaTraceId);
        }
        response.addHeader(BellaContext.BELLA_TRACE_HEADER, bellaTraceId);
        String requestId = UUID.randomUUID().toString();
        BellaContext.getHeaders().put(BellaContext.BELLA_REQUEST_ID_HEADER, requestId);
        response.addHeader(BellaContext.BELLA_REQUEST_ID_HEADER, requestId);
    }

}
