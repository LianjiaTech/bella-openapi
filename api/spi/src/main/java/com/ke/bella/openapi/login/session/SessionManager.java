package com.ke.bella.openapi.login.session;

import com.ke.bella.openapi.Operator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface SessionManager {

    String create(Operator sessionInfo, HttpServletRequest request, HttpServletResponse response);

    String create(String secret, HttpServletRequest request, HttpServletResponse response);

    Operator getSession(HttpServletRequest request);

    void destroySession(HttpServletRequest request, HttpServletResponse response);

    void renew(HttpServletRequest request);

    boolean userRepoInitialized();
}
