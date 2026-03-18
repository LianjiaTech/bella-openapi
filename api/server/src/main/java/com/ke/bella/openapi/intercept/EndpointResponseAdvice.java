package com.ke.bella.openapi.intercept;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.BellaException;
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
        OpenapiResponse openapiResponse = body instanceof OpenapiResponse ? (OpenapiResponse) body : new OpenapiResponse();
        applyErrorStatus(openapiResponse, response);
        if(isAsyncRequest(request)) {
            return body;
        }
        if(EndpointContext.getProcessData().getResponse() == null) {
            String requestId = EndpointContext.getProcessData().getRequestId();
            if(openapiResponse.getError() != null) {
                logError(openapiResponse.getError().getHttpCode(), requestId, openapiResponse.getError().getMessage(), null);
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
        BellaException e = BellaException.fromException(exception);
        logError(e.getHttpCode(), requestId, e.getMessage(), e);
        OpenapiResponse.OpenapiError error = e.convertToOpenapiError();
        OpenapiResponse openapiResponse = OpenapiResponse.errorResponse(error);
        if(e instanceof BellaException.SafetyCheckException) {
            openapiResponse.setSensitives(((BellaException.SafetyCheckException) e).getSensitive());
        }
        return openapiResponse;
    }

    private void applyErrorStatus(OpenapiResponse openapiResponse, ServerHttpResponse response) {
        if(openapiResponse.getError() == null) {
            response.setStatusCode(HttpStatus.OK);
        } else {
            Integer httpCode = openapiResponse.getError().getHttpCode();
            response.setStatusCode(httpCode == null ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.valueOf(httpCode));
        }
    }

    private boolean isAsyncRequest(ServerHttpRequest request) {
        if(request instanceof ServletServerHttpRequest) {
            return Boolean.TRUE.equals(((ServletServerHttpRequest) request).getServletRequest().getAttribute(ASYNC_REQUEST_MARKER));
        }
        return false;
    }

    private void logError(Integer httpCode, String requestId, String msg, Throwable e) {
        String str = "req_id :" + requestId + ",msg:" + msg;
        // 输出req_id方便根据req_id查询能力点日志
        if(httpCode == 500) {
            log.error(str, e);
        } else if(httpCode == 400 || httpCode == 401) {
            log.info(str, e);
        } else {
            log.warn(str, e);
        }
    }
}
