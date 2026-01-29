package com.ke.bella.openapi.common.exception;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import javax.servlet.ServletException;
import java.io.IOException;

public abstract class BellaException extends RuntimeException {

    protected BellaException(String message) {
        super(message);
    }

    protected BellaException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public static BellaException fromResponse(int httpCode, String message) {
        return new BellaException(message) {
            @Override
            public Integer getHttpCode() {
                return httpCode;
            }

            @Override
            public String getType() {
                return "Internal Exception";
            }
        };
    }

    public static BellaException fromException(Throwable e) {
        if(e instanceof BellaException) {
            return (BellaException) e;
        }
        if(e.getCause() instanceof BellaException) {
            return (BellaException) e.getCause();
        }
        return new BellaException(e.getMessage(), e) {
            @Override
            public Integer getHttpCode() {
                if(e instanceof IllegalArgumentException
                        || e instanceof UnsupportedOperationException
                        || e instanceof ServletException
                        || e instanceof MethodArgumentNotValidException
                        || e instanceof HttpMessageConversionException) {
                    return 400;
                }
                if(e instanceof IOException) {
                    return 502;
                }
                return 500;
            }

            @Override
            public String getType() {
                if(e instanceof IllegalArgumentException) {
                    return "Illegal Argument";
                }
                if(e instanceof UnsupportedOperationException) {
                    return "Unsupported Operation";
                }
                return "Internal Exception";
            }
        };
    }

    /**
     * 异常对应的http状态码
     *
     * @return
     */
    public abstract Integer getHttpCode();

    /**
     * 异常 type
     *
     * @return
     */
    public abstract String getType();

    public OpenapiResponse.OpenapiError convertToOpenapiError() {
        if(this instanceof ChannelException) {
            return ((ChannelException) this).getResponse();
        } else if(this instanceof BellaException.SafetyCheckException) {
            return new OpenapiResponse.OpenapiError(this.getType(), this.getMessage(), this.getHttpCode(),
                    ((SafetyCheckException) this).getSensitive());
        } else {
            return new OpenapiResponse.OpenapiError(this.getType(), this.getMessage(), this.getHttpCode());
        }
    }

    public static class RateLimitException extends BellaException {
        public RateLimitException(String message) {
            super(message);
        }

        @Override
        public Integer getHttpCode() {
            return HttpStatus.TOO_MANY_REQUESTS.value();
        }

        @Override
        public String getType() {
            return HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase();
        }
    }

    public static class AuthorizationException extends BellaException {
        public AuthorizationException(String message) {
            super(message);
        }

        @Override
        public Integer getHttpCode() {
            return HttpStatus.UNAUTHORIZED.value();
        }

        @Override
        public String getType() {
            return HttpStatus.UNAUTHORIZED.getReasonPhrase();
        }
    }

    @Getter
    public static class SafetyCheckException extends BellaException {
        protected final Integer httpCode;
        protected final String type;
        protected final Object sensitive;

        public SafetyCheckException(Object sensitive) {
            super("safety_check_no_pass");
            this.httpCode = 400;
            this.type = "safety_check";
            this.sensitive = sensitive;
        }
    }

    @Getter
    public static class ChannelException extends BellaException {

        protected final Integer httpCode;
        protected final String type;
        private final OpenapiResponse.OpenapiError response;

        public ChannelException(Integer httpCode, String message) {
            this(httpCode, "Channel Exception", message);
        }

        public ChannelException(Integer httpCode, String type, String message) {
            this(httpCode, type, message, new OpenapiResponse.OpenapiError(type, message, httpCode));
        }

        public ChannelException(Integer httpCode, String type, String message, OpenapiResponse.OpenapiError error) {
            super(message);
            this.httpCode = httpCode >= 500 ? HttpStatus.SERVICE_UNAVAILABLE.value() : httpCode;
            if(httpCode >= 500) {
                message = "供应商返回：code: " + httpCode + " message: " + message;
            }
            this.type = type;
            if(error == null) {
                this.response = new OpenapiResponse.OpenapiError(type, message, httpCode);
            } else {
                error.setHttpCode(httpCode);
                this.response = error;
            }
        }
    }

    @Getter
    public static class ClientNotLoginException extends BellaException {

        private final String redirectUrl;

        public ClientNotLoginException(String redirectUrl) {
            super("Need to login");
            this.redirectUrl = redirectUrl;
        }

        @Override
        public Integer getHttpCode() {
            return 401;
        }

        @Override
        public String getType() {
            return "No Login";
        }
    }
}
