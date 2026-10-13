package com.ke.bella.openapi.common.exception;

import org.springframework.http.HttpStatus;

/**
 * function: 业务参数校验异常
 *
 * @author chenhongliang001
 */
public class BizParamCheckException extends BellaException {
    private String model;

    public BizParamCheckException(String message) {
        super(message);
    }

    public BizParamCheckException withModel(String model) {
        if(model != null && !model.trim().isEmpty()) {
            this.model = model;
        }
        return this;
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        if(model == null || model.trim().isEmpty()) {
            return message;
        }
        return message + ", model: " + model;
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
