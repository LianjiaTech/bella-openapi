package com.ke.bella.openapi.common.exception;

import org.springframework.http.HttpStatus;

/**
 * function: 业务参数校验异常
 *
 * @author chenhongliang001
 */
public class BizParamCheckException extends BellaException {

    public BizParamCheckException(String message) {
        super(message);
    }

    @Override
    public Integer getHttpCode() {
        return HttpStatus.BAD_REQUEST.value();
    }

    @Override
    public String getType() {
        return "Illegal Argument";
    }
}
