package com.ke.bella.openapi.intercept;

import static com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor.ASYNC_REQUEST_MARKER;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.endpoints.async.AsyncExecutor.AsyncAttributes;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogger;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice(annotations = EndpointAPI.class)
@EndpointAPI
@Slf4j
public class EndpointResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final HttpStatus UNKNOWN_ERROR_STATUS = HttpStatus.INTERNAL_SERVER_ERROR;
    private static final HttpStatus INVALID_UPSTREAM_STATUS = HttpStatus.BAD_GATEWAY;

    @Autowired
    private EndpointLogger logger;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        boolean restoredAsyncContext = restoreEndpointDeferredContext(request);
        try {
            OpenapiResponse openapiResponse = body instanceof OpenapiResponse ? (OpenapiResponse) body : new OpenapiResponse();
            applyErrorStatus(openapiResponse, response);
            if(isAsyncRequest(request) && !restoredAsyncContext) {
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
        } finally {
            if(restoredAsyncContext) {
                clearEndpointDeferredContext(request);
                EndpointContext.clearAll();
            }
        }
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public OpenapiResponse exceptionHandler(Exception exception, HttpServletRequest request) {
        restoreEndpointDeferredContext(request);
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
            if(httpCode == null) {
                response.setStatusCode(UNKNOWN_ERROR_STATUS);
                return;
            }
            if(httpCode < 100 || httpCode > 599) {
                response.setStatusCode(INVALID_UPSTREAM_STATUS);
                return;
            }
            HttpStatus status = HttpStatus.resolve(httpCode);
            if(status != null) {
                response.setStatusCode(status);
            } else if(response instanceof ServletServerHttpResponse) {
                ((ServletServerHttpResponse) response).getServletResponse().setStatus(httpCode);
            } else {
                response.setStatusCode(INVALID_UPSTREAM_STATUS);
            }
        }
    }

    private boolean isAsyncRequest(ServerHttpRequest request) {
        if(request instanceof ServletServerHttpRequest) {
            return Boolean.TRUE.equals(((ServletServerHttpRequest) request).getServletRequest().getAttribute(ASYNC_REQUEST_MARKER));
        }
        return false;
    }

    private boolean restoreEndpointDeferredContext(ServerHttpRequest request) {
        if(request instanceof ServletServerHttpRequest) {
            return restoreEndpointDeferredContext(((ServletServerHttpRequest) request).getServletRequest());
        }
        return false;
    }

    private boolean restoreEndpointDeferredContext(HttpServletRequest request) {
        if(!Boolean.TRUE.equals(request.getAttribute(ASYNC_REQUEST_MARKER))) {
            return false;
        }
        if(!Boolean.TRUE.equals(request.getAttribute(AsyncAttributes.DEFERRED_RESULT))) {
            return false;
        }
        Object processData = request.getAttribute(AsyncAttributes.PROCESS_DATA);
        if(processData instanceof EndpointProcessData) {
            EndpointContext.setProcessData((EndpointProcessData) processData);
            return true;
        }
        return false;
    }

    private void clearEndpointDeferredContext(ServerHttpRequest request) {
        if(request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            servletRequest.removeAttribute(AsyncAttributes.DEFERRED_RESULT);
            servletRequest.removeAttribute(AsyncAttributes.PROCESS_DATA);
        }
    }

    private void logError(Integer httpCode, String requestId, String msg, Throwable e) {
        String str = "req_id :" + requestId + ",msg:" + msg;
        if(httpCode == 500) {
            log.error(str, e);
        } else if(httpCode == 400 || httpCode == 401) {
            log.info(str);
        } else {
            log.warn(str);
        }
    }
}
