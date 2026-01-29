package com.ke.bella.openapi.common.exception;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import javax.servlet.ServletException;
import java.io.IOException;

public abstract class ChannelException extends RuntimeException {

    protected ChannelException(String message) {
        super(message);
    }

    protected ChannelException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public static ChannelException fromResponse(int httpCode, String message) {
        return new ChannelException(message) {
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

    public static ChannelException fromException(Throwable e) {
        if(e instanceof ChannelException) {
            return (ChannelException) e;
        }
        if(e.getCause() instanceof ChannelException) {
            return (ChannelException) e.getCause();
        }
        return new ChannelException(e.getMessage(), e) {
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
        if(this instanceof ChannelException.OpenAIException) {
            return ((ChannelException.OpenAIException) this).getResponse();
        } else if(this instanceof ChannelException.SafetyCheckException) {
            return new OpenapiResponse.OpenapiError(this.getType(), this.getMessage(), this.getHttpCode(),
                    ((SafetyCheckException) this).getSensitive());
        } else {
            return new OpenapiResponse.OpenapiError(this.getType(), this.getMessage(), this.getHttpCode());
        }
    }

    public static class RateLimitException extends ChannelException {
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

    public static class AuthorizationException extends ChannelException {
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
    public static class SafetyCheckException extends ChannelException {
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
    public static class OpenAIException extends ChannelException {

        protected final Integer httpCode;
        protected final String type;
        private final OpenapiResponse.OpenapiError response;

        public OpenAIException(Integer httpCode, String message) {
            this(httpCode, "Channel Exception", message);
        }

        public OpenAIException(Integer httpCode, String type, String message) {
            this(httpCode, type, message, new OpenapiResponse.OpenapiError(type, message, httpCode));
        }

        public OpenAIException(Integer httpCode, String type, String message, OpenapiResponse.OpenapiError error) {
            super(message);
            this.httpCode = httpCode >= 500 ? HttpStatus.SERVICE_UNAVAILABLE.value() : httpCode;
            if(httpCode > 500) {
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
    public static class ClientNotLoginException extends ChannelException {

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
