package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.request.BellaRequestFilter;
import com.ke.bella.openapi.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Author: Stan Sai Date: 2024/8/13 16:50 description:
 */
@Component
public class OpenapiRequestFilter extends BellaRequestFilter {
    public OpenapiRequestFilter() {
        super("openapi");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            super.bellaRequestFilter(request, response);
            EndpointContext.setHeaderInfo(BellaContext.getHeaders());
            EndpointContext.getProcessData().setRequestMillis(DateTimeUtils.getCurrentMills());
            EndpointContext.getProcessData().setRequestTime(DateTimeUtils.getCurrentSeconds());
            EndpointContext.setRequest(request);

            // 从原始请求获取大小并估算（用于 DirectByteBuffer 分配）
            int contentLength = request.getContentLength();
            if (contentLength > 0) {
                // 乘以 1.5 系数：base64 编码 (+33%) + JSON 结构开销 (+10-20%)
                int estimatedSize = (int) (contentLength * 1.5);
                EndpointContext.setSerializedSize(estimatedSize);
            }

            chain.doFilter(request, response);
        } finally {
            BellaContext.clearAll();
            EndpointContext.clearAll();
        }
    }
}
