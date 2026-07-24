package com.ke.bella.openapi.login.cas;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.ke.bella.openapi.login.config.BellaLoginConfiguration.redirectParameter;

public class BellaRedirectFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if(request.getAttribute(redirectParameter) != null) {
            HttpServletResponse res = (HttpServletResponse) response;
            res.sendRedirect(request.getAttribute(redirectParameter).toString());
            return;
        }
        chain.doFilter(request, response);
    }
}
