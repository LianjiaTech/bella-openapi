package com.ke.bella.openapi.intercept;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogger;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice(annotations = EndpointAPI.class)
@EndpointAPI
@Slf4j
public class EndpointResponseAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private EndpointLogger logger;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if(EndpointContext.getProcessData().getResponse() == null) {
            OpenapiResponse openapiResponse = body instanceof OpenapiResponse ? (OpenapiResponse) body : new OpenapiResponse();
            String requestId = EndpointContext.getProcessData().getRequestId();
            if(openapiResponse.getError() == null) {
                response.setStatusCode(HttpStatus.OK);
            } else {
                Integer httpCode = openapiResponse.getError().getHttpCode();
                response.setStatusCode(httpCode == null ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.valueOf(httpCode));
                logError(httpCode, requestId, openapiResponse.getError().getMessage(), null);
            }
            EndpointContext.getProcessData().setResponse(openapiResponse);
        }
        logger.log(EndpointContext.getProcessData());
        return body;
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public OpenapiResponse exceptionHandler(Exception exception) {
        String requestId = EndpointContext.getProcessData().getRequestId();
        ChannelException e = ChannelException.fromException(exception);
        logError(e.getHttpCode(), requestId, e.getMessage(), e);
        OpenapiResponse.OpenapiError error = e.convertToOpenapiError();
        OpenapiResponse openapiResponse = OpenapiResponse.errorResponse(error);
        if(e instanceof ChannelException.SafetyCheckException) {
            openapiResponse.setSensitives(((ChannelException.SafetyCheckException) e).getSensitive());
        }
        return openapiResponse;
    }

    private void logError(Integer httpCode, String requestId, String msg, Throwable e) {
        String str = "req_id :" + requestId + ",msg:" + msg;
        //输出req_id方便根据req_id查询能力点日志
        if(httpCode == 500) {
            LOGGER.error(str, e);
        } else if(httpCode == 400 || httpCode == 401){
            LOGGER.info(str, e);
        } else {
            LOGGER.warn(str, e);
        }
    }
}
