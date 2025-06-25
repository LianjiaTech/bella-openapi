package com.ke.bella.openapi.login.cas;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ke.bella.openapi.login.LoginFilter;
import org.apache.commons.lang3.StringUtils;

import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.login.config.BellaLoginConfiguration;
import com.ke.bella.openapi.login.session.SessionManager;

import static com.ke.bella.openapi.login.LoginFilter.CONSOLE_HEADER;

public class BellaCasLoginFilter implements Filter {
    private final String loginUrl;
    private final String clientHost;
    private final String serviceUri;
    private final boolean clientSupport;
    private final String authorizationHeader;
    private final String clientIndex;
    private final SessionManager sessionManager;

    public BellaCasLoginFilter(String loginUrl, String clientHost, String serviceUri, boolean clientSupport,
            String authorizationHeader, String clientIndex, SessionManager sessionManager) {
        this.loginUrl = loginUrl;
        this.clientHost = clientHost;
        this.serviceUri = serviceUri;
        this.clientSupport = clientSupport;
        this.authorizationHeader = authorizationHeader;
        this.clientIndex = clientIndex;
        this.sessionManager = sessionManager;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if(httpRequest.getRequestURI().equals(serviceUri)) {
            //不处理cas服务的登出请求
            return;
        }
        if(StringUtils.isNotBlank(authorizationHeader)) {
            String auth = httpRequest.getHeader(authorizationHeader);
            if(StringUtils.isNotBlank(auth)) {
                chain.doFilter(request, response);
                return;
            }
        }
        if(!"true".equals(httpRequest.getHeader(CONSOLE_HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        Operator operator = sessionManager.getSession(httpRequest);
        if(operator == null) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            StringBuilder loginBuilder = new StringBuilder(loginUrl);
            loginBuilder.append("?service=");
            StringBuilder serviceBuilder = new StringBuilder(clientHost).append(serviceUri).append("?")
                    .append(BellaLoginConfiguration.redirectParameter).append("=");
            if(!clientSupport) {
                serviceBuilder.append(clientIndex);
            }
            String encodedService = URLEncoder.encode(serviceBuilder.toString(), StandardCharsets.UTF_8.toString());
            loginBuilder.append(encodedService);
            if(clientSupport) {
                httpResponse.setStatus(401);
                httpResponse.setHeader(LoginFilter.REDIRECT_HEADER, loginBuilder.toString());
            } else {
                httpResponse.sendRedirect(loginBuilder.toString());
            }
            return;
        }
        chain.doFilter(request, response);
    }


}
