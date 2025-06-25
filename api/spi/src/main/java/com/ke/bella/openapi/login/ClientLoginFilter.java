package com.ke.bella.openapi.login;

import com.ke.bella.openapi.common.exception.ChannelException;

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
        } catch (ChannelException.ClientNotLoginException clientNotLoginException) {
            httpResponse.setStatus(401);
            httpResponse.setHeader(LoginFilter.REDIRECT_HEADER, clientNotLoginException.getRedirectUrl());
        } catch (ChannelException channelException) {
            httpResponse.sendError(channelException.getHttpCode(), channelException.getMessage());
        } catch (Exception exception) {
            httpResponse.sendError(500, exception.getMessage());
        }
    }
}
