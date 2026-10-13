package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.common.exception.BellaException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AwsExceptionUtils {

    private static final Pattern RESPONSE_CODE_PATTERN = Pattern.compile("Response Code:\\s*(\\d{3})");
    private static final ExecutionAttribute<ResponseStatusCapture> RESPONSE_STATUS_CAPTURE =
            new ExecutionAttribute<>("bellaAwsResponseStatusCapture");
    private static final ExecutionInterceptor RESPONSE_STATUS_INTERCEPTOR = new ResponseStatusInterceptor();

    private AwsExceptionUtils() {
    }

    public static ExecutionAttribute<ResponseStatusCapture> responseStatusAttribute() {
        return RESPONSE_STATUS_CAPTURE;
    }

    public static ExecutionInterceptor responseStatusInterceptor() {
        return RESPONSE_STATUS_INTERCEPTOR;
    }

    public static BellaException toBellaException(Throwable throwable) {
        return toBellaException(throwable, null);
    }

    public static BellaException toBellaException(Throwable throwable, Integer responseCode) {
        Throwable cause = unwrap(throwable);
        if(cause instanceof BellaException) {
            return (BellaException) cause;
        }
        if(cause instanceof SdkServiceException) {
            SdkServiceException serviceException = (SdkServiceException) cause;
            return new BellaException.ChannelException(serviceException.statusCode(), serviceException.getMessage());
        }
        if(responseCode != null && responseCode >= 400 && responseCode <= 599) {
            return new BellaException.ChannelException(responseCode, cause.getMessage());
        }
        if(cause instanceof SdkClientException) {
            Integer parsedResponseCode = responseCode(cause.getMessage());
            if(parsedResponseCode != null) {
                return new BellaException.ChannelException(parsedResponseCode, cause.getMessage());
            }
        }
        return BellaException.fromException(cause);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static Integer responseCode(String message) {
        if(message == null) {
            return null;
        }
        Matcher matcher = RESPONSE_CODE_PATTERN.matcher(message);
        if(!matcher.find()) {
            return null;
        }
        int responseCode = Integer.parseInt(matcher.group(1));
        return responseCode >= 400 && responseCode <= 599 ? responseCode : null;
    }

    public static final class ResponseStatusCapture {

        private final AtomicInteger responseCode = new AtomicInteger();

        private void responseCode(int responseCode) {
            this.responseCode.set(responseCode);
        }

        public Integer responseCode() {
            int value = responseCode.get();
            return value == 0 ? null : value;
        }
    }

    private static final class ResponseStatusInterceptor implements ExecutionInterceptor {

        @Override
        public void afterTransmission(Context.AfterTransmission context, ExecutionAttributes executionAttributes) {
            capture(executionAttributes, context.httpResponse().statusCode());
        }

        @Override
        public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes executionAttributes) {
            context.httpResponse().ifPresent(response -> capture(executionAttributes, response.statusCode()));
        }

        private void capture(ExecutionAttributes executionAttributes, int responseCode) {
            ResponseStatusCapture responseStatusCapture = executionAttributes.getAttribute(RESPONSE_STATUS_CAPTURE);
            if(responseStatusCapture != null) {
                responseStatusCapture.responseCode(responseCode);
            }
        }
    }
}
