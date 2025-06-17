package com.ke.bella.openapi.login.session;

import com.ke.bella.openapi.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import sun.reflect.generics.reflectiveObjects.NotImplementedException; // Keep if other methods use it

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// Import SessionProperty if not already (assuming it's in this package or a common one)
// import com.ke.bella.openapi.login.session.SessionProperty; // Adjust path if needed

public class HttpSessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(HttpSessionManager.class);

    private final RestTemplate restTemplate;
    private final String bellaOpenApiBaseUrl;
    private final SessionProperty sessionProperty; // Added field

    public HttpSessionManager(RestTemplate restTemplate,
                              @Value("${bella.openapi.base-url}") String bellaOpenApiBaseUrl,
                              SessionProperty sessionProperty) { // Added SessionProperty
        this.restTemplate = restTemplate;
        this.bellaOpenApiBaseUrl = bellaOpenApiBaseUrl;
        this.sessionProperty = sessionProperty; // Initialize field
    }

    private String extractToken(HttpServletRequest request) {
        if (request == null || this.sessionProperty == null || this.sessionProperty.getCookieName() == null) {
            log.trace("Request, sessionProperty, or cookie name is null. Cannot extract token.");
            return null;
        }
        javax.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (javax.servlet.http.Cookie cookie : cookies) {
                if (this.sessionProperty.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
            log.trace("Cookie with name '{}' not found.", this.sessionProperty.getCookieName());
        } else {
            log.trace("No cookies in request.");
        }
        return null;
    }

    @Override
    public Operator getSession(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            // log.warn("No session token found in request (cookie: {}).", this.sessionProperty != null ? this.sessionProperty.getCookieName() : "unknown");
            // Avoid NPE if sessionProperty itself is null, though constructor should prevent this if called by Spring
             if (this.sessionProperty != null && this.sessionProperty.getCookieName() != null) {
                log.warn("No session token found in request (cookie: {}).", this.sessionProperty.getCookieName());
            } else {
                log.warn("No session token found in request and sessionProperty or cookieName is not configured.");
            }
            return null;
        }

        String url = bellaOpenApiBaseUrl + "/openapi/login?token=" + token;
        log.debug("Attempting to validate token via HTTP GET to: {}", url);

        try {
            // Using exchange to get ResponseEntity for better error status handling
            ResponseEntity<Operator> response = restTemplate.exchange(url, HttpMethod.GET, null, Operator.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Token validation successful for token (ending with ...{}).", token.length() > 4 ? token.substring(token.length() - 4) : token);
                return response.getBody();
            } else {
                // This case might not be hit if non-2xx throws HttpClientErrorException
                log.warn("Token validation failed for token (ending with ...{}). Status: {}", token.length() > 4 ? token.substring(token.length() - 4) : token, response.getStatusCode());
                return null;
            }
        } catch (HttpClientErrorException e) {
            // Handles 4xx and 5xx errors specifically
            log.warn("HTTP error during token validation for token (ending with ...{}): {} - {}",
                     token.length() > 4 ? token.substring(token.length() - 4) : token,
                     e.getStatusCode(),
                     e.getResponseBodyAsString());
            return null;
        } catch (RestClientException e) {
            log.error("Error calling /openapi/login for token validation (token ending with ...{}): {}",
                      token.length() > 4 ? token.substring(token.length() - 4) : token,
                      e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void destroySession(HttpServletRequest request, HttpServletResponse response) {
        String token = extractToken(request);
        if (token == null) {
            if (this.sessionProperty != null && this.sessionProperty.getCookieName() != null) {
                log.warn("No session token found for destruction (cookie: {}).", this.sessionProperty.getCookieName());
            } else {
                log.warn("No session token found for destruction and sessionProperty or cookieName is not configured.");
            }
            return;
        }

        String url = bellaOpenApiBaseUrl + "/openapi/logout?token=" + token;
        log.debug("Attempting to destroy session via HTTP POST to: {}", url);

        try {
            // Using exchange for consistency and potential error handling
            ResponseEntity<Void> resp = restTemplate.exchange(url, HttpMethod.POST, null, Void.class);
             if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("Session destruction successful for token (ending with ...{}).", token.length() > 4 ? token.substring(token.length() - 4) : token);
            } else {
                log.warn("Session destruction failed for token (ending with ...{}). Status: {}", token.length() > 4 ? token.substring(token.length() - 4) : token, resp.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.warn("HTTP error during session destruction for token (ending with ...{}): {} - {}",
                     token.length() > 4 ? token.substring(token.length() - 4) : token,
                     e.getStatusCode(),
                     e.getResponseBodyAsString());
        } catch (RestClientException e) {
            log.error("Error calling /openapi/logout for session destruction (token ending with ...{}): {}",
                      token.length() > 4 ? token.substring(token.length() - 4) : token,
                      e.getMessage(), e);
        }
    }

    // ... (other methods: renew, create, ticket methods, userRepoInitialized remain unchanged) ...
    @Override
    public void renew(HttpServletRequest request) {
        log.debug("HttpSessionManager.renew() called, no action taken as per design.");
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
    public void saveTicket(String ticket) {
        log.warn("HttpSessionManager.saveTicket(String) called but is not implemented.");
        throw new NotImplementedException();
    }

    @Override
    public boolean isValidTicket(String ticket) {
        log.warn("HttpSessionManager.isValidTicket(String) called but is not implemented.");
        throw new NotImplementedException();
    }

    @Override
    public void removeTicket(String ticket) {
        log.warn("HttpSessionManager.removeTicket(String) called but is not implemented.");
        throw new NotImplementedException();
    }

    @Override
    public boolean userRepoInitialized() {
        log.debug("HttpSessionManager.userRepoInitialized() called, returning false as no direct IUserRepo.");
        return false;
    }
}
