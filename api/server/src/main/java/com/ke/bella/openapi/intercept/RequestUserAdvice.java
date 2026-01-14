package com.ke.bella.openapi.intercept;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

@RestControllerAdvice
@Slf4j
public class RequestUserAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> clazz = methodParameter.getContainingClass();
        return clazz.getName().startsWith("com.ke.bella.openapi.endpoints.");
    }

    @Override
    public Object afterBodyRead(Object request, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        String user = null;
        if(request instanceof UserRequest) {
            user = ((UserRequest) request).getUser();
        }
        if(StringUtils.isEmpty(user)) {
            user = EndpointContext.getRequest().getHeader("ucid");
        }
        EndpointContext.getProcessData().setUser(user);
        return super.afterBodyRead(request, inputMessage, parameter, targetType, converterType);
    }
}
