package com.ke.bella.openapi.login.oauth;

import com.ke.bella.openapi.BellaResponse;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.login.session.SessionManager;
import com.ke.bella.openapi.login.session.TicketManager;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OAuthLoginFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthLoginFilter.class);

    private final Map<String, OAuthService> oauthServices;
    private final SessionManager sessionManager;
    private final TicketManager ticketManager;
    private final OAuthProperties properties;

    public OAuthLoginFilter(List<OAuthService> services, SessionManager sessionManager, TicketManager ticketManager, OAuthProperties properties) {
        this.ticketManager = ticketManager;
        this.oauthServices = new HashMap<>();
        for (OAuthService service : services) {
            this.oauthServices.put(service.getProviderType(), service);
        }
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();
        
        // Handle OAuth callback
        if (requestUri.startsWith("/openapi/oauth/callback/")) {
            String provider = requestUri.substring("/openapi/oauth/callback/".length());
            handleCallback(provider, httpRequest, httpResponse);
            return;
        }

        // Handle OAuth config request
        if (requestUri.equals("/openapi/oauth/config")) {
            handleOAuthConfig(httpRequest, httpResponse);
            return;
        }

        chain.doFilter(request, response);
    }

    private void handleOAuthConfig(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String redirect = request.getParameter("redirect");
        // 将 redirect 参数编码到 state 中
        String state = UUID.randomUUID() + (StringUtils.isNotBlank(redirect) ? ":" + redirect : "");
        ticketManager.saveTicket(state);


        List<Map<String, Object>> providers = new ArrayList<>();
        for (String type : oauthServices.keySet()) {
            OAuthService service = oauthServices.get(type);
            String authUrl = service.getAuthorizationUrl(state);
            
            Map<String, Object> provider = new HashMap<>();
            provider.put("type", service.getProviderType());
            provider.put("authUrl", authUrl);
            providers.add(provider);
        }

        BellaResponse<List<Map<String, Object>>> bellaResponse = new BellaResponse();
        bellaResponse.setCode(200);
        bellaResponse.setData(providers);
        response.setContentType("application/json");
        response.getWriter().write(JacksonUtils.serialize(bellaResponse));
    }

    private void handleCallback(String provider, HttpServletRequest request, HttpServletResponse response) throws IOException {
        OAuthService service = oauthServices.get(provider);
        if (service == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid provider");
            return;
        }

        String error = request.getParameter("error");
        if (StringUtils.isNotBlank(error)) {
            LOGGER.error("{} OAuth error: {}", provider, error);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            return;
        }

        String code = request.getParameter("code");
        String state = request.getParameter("state");


        if (!ticketManager.isValidTicket(state)) {
            LOGGER.error("OAuth validation failed - code: {}, state: {}", code, state);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid state");
            return;
        }

        // 从 state 中解析出 redirect 参数
        String redirect = null;
        if (state.contains(":")) {
            redirect = state.substring(state.indexOf(":") + 1);
        }

        Operator operator = service.handleCallback(code, state);
        if (operator == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Failed to get user info");
            return;
        }
        sessionManager.create(operator, request, response);
        ticketManager.removeTicket(state);
        response.sendRedirect(StringUtils.isNotBlank(redirect) ? redirect : properties.getClientIndex());
    }
}
