package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.request.BellaRequestFilter;
import com.ke.bella.openapi.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public class OpenapiRequestFilter extends BellaRequestFilter {
    public OpenapiRequestFilter() {
        super("openapi");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        try {
            super.bellaRequestFilter(request, response);
            log.info("[traceId={}] Request  : {} {}", BellaContext.getTraceId(), request.getMethod(), request.getRequestURI());
            EndpointContext.setHeaderInfo(BellaContext.getHeaders());
            EndpointContext.getProcessData().setRequestMillis(DateTimeUtils.getCurrentMills());
            EndpointContext.getProcessData().setRequestTime(DateTimeUtils.getCurrentSeconds());
            EndpointContext.setRequest(request);
            chain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            String akCode = EndpointContext.getProcessData().getAkCode();
            log.info("[traceId={}][akCode={}] Response : {} {} status={} cost={}ms",
                    BellaContext.getTraceId(),
                    akCode != null ? akCode : "-",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    cost);
            BellaContext.clearAll();
            EndpointContext.clearAll();
        }
    }
}
