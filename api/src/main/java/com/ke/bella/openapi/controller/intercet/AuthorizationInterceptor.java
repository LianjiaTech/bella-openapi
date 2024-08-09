package com.ke.bella.openapi.controller.intercet;

import com.ke.bella.openapi.db.AuthorizationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Author: Stan Sai Date: 2024/7/31 20:28 description:
 */
@Component
public class AuthorizationInterceptor extends HandlerInterceptorAdapter {

    @Value("${spring.profiles.active}")
    private String profile;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        //todo: ak鉴权，信息加入AuthorizationContext
        if(profile.equals("dev")) {
            AuthorizationContext.setSystemUser();
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthorizationContext.clearAll();
    }
}
