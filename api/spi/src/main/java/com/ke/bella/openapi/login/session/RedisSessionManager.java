package com.ke.bella.openapi.login.session;

import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.login.user.IUserRepo;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisSessionManager implements SessionManager, TicketManager { // Changed
                                                                            // class
                                                                            // name
                                                                            // and
                                                                            // added
                                                                            // 'implements
                                                                            // SessionManager'

    private final SessionProperty sessionProperty;
    private final RedisTemplate<String, Operator> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    @Setter
    private IUserRepo userRepo;
    private String sessionPrefix = "bella-openapi-session-user:";
    private String ticketPrefix = "bella-openapi-oauth-ticket:";

    public RedisSessionManager(SessionProperty sessionProperty, RedisTemplate<String, Operator> redisTemplate,
            StringRedisTemplate stringRedisTemplate) { // Constructor name
                                                       // changed
        this.sessionProperty = sessionProperty;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        if(sessionProperty.getSessionPrefix() != null) {
            sessionPrefix = sessionProperty.getSessionPrefix();
        }
    }

    @Override
    public boolean userRepoInitialized() {
        return userRepo != null;
    }

    @Override
    public String create(Operator sessionInfo, HttpServletRequest request, HttpServletResponse response) {
        // 如果配置了用户持久化，则进行持久化
        if(userRepo != null) {
            sessionInfo = userRepo.persist(sessionInfo);
        }
        return createSession(sessionInfo, request, response);
    }

    @Override
    public String create(String secret, HttpServletRequest request, HttpServletResponse response) {
        if(userRepo == null) {
            throw new NotImplementedException();
        }
        Operator operator = userRepo.checkSecret(secret);
        if(operator == null) {
            return null;
        }
        return createSession(operator, request, response);
    }

    private String createSession(Operator sessionInfo, HttpServletRequest request, HttpServletResponse response) {
        String id = UUID.randomUUID().toString();
        ValueOperations<String, Operator> ops = redisTemplate.opsForValue();
        ops.set(sessionPrefix + id, sessionInfo, sessionProperty.getMaxInactiveInterval(), TimeUnit.MINUTES);
        addCookie(id, request, response);
        return id;
    }

    @Override
    public Operator getSession(HttpServletRequest request) {
        String id = findSessionId(request);
        if(id == null) {
            return null;
        }
        return loadById(id);
    }

    @Override
    public void destroySession(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = findSessionId(request);
        if(sessionId != null) {
            deleteById(sessionId);
            Cookie cookie = new Cookie(sessionProperty.getCookieName(), null);
            cookie.setMaxAge(0);
            cookie.setPath("/"); // Consider making path configurable or same as
                                 // addCookie
            response.addCookie(cookie);
        }
    }

    @Override
    public void renew(HttpServletRequest request) {
        try {
            String id = findSessionId(request);
            if(id != null) {
                expire(id, sessionProperty.getMaxInactiveInterval());
            }
        } catch (Exception e) {
            log.warn("session renew error", e);
        }
    }

    @Override
    public void saveTicket(String ticket) {
        stringRedisTemplate.opsForValue().set(getTicketKey(ticket), ticket, 10, TimeUnit.MINUTES);
    }

    @Override
    public boolean isValidTicket(String ticket) {
        // Ensure stringRedisTemplate is not null if this method can be called
        // before full initialization
        Boolean hasKey = stringRedisTemplate.hasKey(getTicketKey(ticket));
        return Boolean.TRUE.equals(hasKey); // Handle null from hasKey
    }

    @Override
    public void removeTicket(String ticket) {
        stringRedisTemplate.delete(getTicketKey(ticket));
    }

    private void deleteById(String id) {
        redisTemplate.delete(sessionPrefix + id);
    }

    private Operator loadById(String id) {
        ValueOperations<String, Operator> ops = redisTemplate.opsForValue();
        return ops.get(sessionPrefix + id);
    }

    private void expire(String id, int maxInactiveInterval) {
        redisTemplate.expire(sessionPrefix + id, maxInactiveInterval, TimeUnit.MINUTES);
    }

    private String findSessionId(HttpServletRequest request) {
        if(request != null) {
            Cookie[] cookies = request.getCookies();
            if(cookies != null) {
                for (Cookie cookie : cookies) {
                    if(cookie.getName().equals(sessionProperty.getCookieName())) {
                        return cookie.getValue();
                    }
                }
            }
        }
        return null;
    }

    private void addCookie(String id, HttpServletRequest request, HttpServletResponse response) {
        if(request != null && response != null) {
            Cookie cookie = new Cookie(sessionProperty.getCookieName(), id);
            cookie.setMaxAge(sessionProperty.getCookieMaxAge());

            String redirectUrl = request.getParameter("redirect");
            cookie.setSecure(!(StringUtils.isNotBlank(redirectUrl) && redirectUrl.startsWith("http://")));

            if(StringUtils.isBlank(sessionProperty.getCookieContextPath())) {
                cookie.setPath("/");
            } else {
                cookie.setPath(sessionProperty.getCookieContextPath());
            }

            if(StringUtils.isNotBlank(sessionProperty.getCookieDomain())) {
                cookie.setDomain(sessionProperty.getCookieDomain());
            }
            cookie.setHttpOnly(true);
            try {
                if(StringUtils.isNotBlank(redirectUrl)) {
                    String host = new URL(redirectUrl).getHost();
                    if("localhost".equals(host)) {
                        cookie.setDomain(host);
                    }
                }
            } catch (Exception e) {
                // ignore domain setting error
            }

            response.addCookie(cookie);
        }
    }

    private String getTicketKey(String ticket) {
        return ticketPrefix + ticket;
    }

}
