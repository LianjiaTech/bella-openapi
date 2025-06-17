package com.ke.bella.openapi.login.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.openapi.BellaResponse;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

public class HttpSessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(HttpSessionManager.class);
    private final String bellaOpenApiBaseUrl;
    private final SessionProperty sessionProperty; // Added field

    public HttpSessionManager(String bellaOpenApiBaseUrl,
                              SessionProperty sessionProperty) {
        this.bellaOpenApiBaseUrl = bellaOpenApiBaseUrl;
        this.sessionProperty = sessionProperty;
    }

    private String extractCookie(HttpServletRequest request) {
        if (request == null || this.sessionProperty == null || this.sessionProperty.getCookieName() == null) {
            return "";
        }
        javax.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (javax.servlet.http.Cookie cookie : cookies) {
                if (this.sessionProperty.getCookieName().equals(cookie.getName())) {
                    return cookie.getName() + "=" + cookie.getValue();
                }
            }
            log.trace("Cookie with name '{}' not found.", this.sessionProperty.getCookieName());
        } else {
            log.trace("No cookies in request.");
        }
        return "";
    }

    @Override
    public Operator getSession(HttpServletRequest request) {
        return Optional.ofNullable(HttpUtils.httpRequest(new Request.Builder().url(bellaOpenApiBaseUrl + "/openapi/userInfo")
                .header("Cookie", extractCookie(request)).build(), new TypeReference<BellaResponse<Operator>>(){}))
                .orElseThrow(() -> new ChannelException.AuthorizationException("Authorization Failed"))
                .getData();
    }

    @Override
    public void destroySession(HttpServletRequest request, HttpServletResponse response) {
        HttpUtils.doHttpRequest(new Request.Builder().url(bellaOpenApiBaseUrl + "/openapi/logout")
                .header("Cookie", extractCookie(request)).build());
    }

    @Override
    public void renew(HttpServletRequest request) {
    }

    @Override
    public String create(Operator sessionInfo, HttpServletRequest request, HttpServletResponse response) {
        log.warn("HttpSessionManager.create(Operator, ...) called but is not implemented.");
        throw new NotImplementedException();
    }

    @Override
    public String create(String secret, HttpServletRequest request, HttpServletResponse response) {
        log.warn("HttpSessionManager.create(String, ...) called but is not implemented.");
        throw new NotImplementedException();
    }

    @Override
    public boolean userRepoInitialized() {
        return false;
    }
}
