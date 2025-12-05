package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.ChannelException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Interceptor to validate direct mode access.
 * Only HIGH role API keys can use direct mode.
 */
@Component
@Order(100)
public class DirectModeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Check if direct mode is enabled
        if (!BellaContext.isDirectMode()) {
            return true;
        }

        // Validate that X-BELLA-MODEL header is present
        String directModel = BellaContext.getDirectModel();
        if (StringUtils.isEmpty(directModel)) {
            throw new ChannelException.AuthorizationException(
                    "Direct mode requires X-BELLA-MODEL header");
        }

        // Validate API key role
        ApikeyInfo apikeyInfo = EndpointContext.getApikey();
        if (apikeyInfo == null) {
            throw new ChannelException.AuthorizationException(
                    "API key not found in context");
        }

        // Only HIGH role can use direct mode
        if (!EntityConstants.HIGH.equals(apikeyInfo.getRoleCode())) {
            throw new ChannelException.AuthorizationException(
                    "Direct mode requires HIGH role API key. Current role: " + apikeyInfo.getRoleCode());
        }

        return true;
    }
}
