package com.ke.bella.openapi.login;

import com.ke.bella.openapi.common.exception.BellaException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ClientLoginFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        try {
            filterChain.doFilter(request, response);
        } catch (BellaException.ClientNotLoginException clientNotLoginException) {
            httpResponse.setStatus(401);
            httpResponse.setHeader(LoginFilter.REDIRECT_HEADER, clientNotLoginException.getRedirectUrl());
        } catch (BellaException bellaException) {
            httpResponse.sendError(bellaException.getHttpCode(), bellaException.getMessage());
        } catch (Exception exception) {
            httpResponse.sendError(500, exception.getMessage());
        }
    }
}
