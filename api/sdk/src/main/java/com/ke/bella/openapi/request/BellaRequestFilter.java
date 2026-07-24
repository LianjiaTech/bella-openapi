package com.ke.bella.openapi.request;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.client.OpenapiClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
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
    private final OpenapiClient openapiClient;

    public BellaRequestFilter(String serviceId) {
        this(serviceId, null);
    }

    public BellaRequestFilter(String serviceId, OpenapiClient client) {
        Assert.isTrue(StringUtils.isNotEmpty(serviceId), "serviceId不能为空");
        this.serviceId = serviceId;
        this.openapiClient = client;
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

        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if(auth != null && openapiClient != null) {
            ApikeyInfo apikeyInfo = verifyAuthHeader(auth, openapiClient);
            if(apikeyInfo != null) {
                BellaContext.setApikey(apikeyInfo);
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

    protected ApikeyInfo verifyAuthHeader(String auth, OpenapiClient client) {
        String ak;
        if(auth.startsWith("Bearer ")) {
            ak = auth.substring(7);
        } else {
            return null;
        }
        ApikeyInfo info = client.whoami(ak);
        if(info != null) {
            info.setApikey(ak);
        }
        return info;
    }
}
