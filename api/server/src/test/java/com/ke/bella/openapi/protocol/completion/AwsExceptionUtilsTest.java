package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.common.exception.BellaException;
import org.junit.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AwsExceptionUtilsTest {

    @Test
    public void restoresResponseCodeFromSdkErrorResponseUnmarshalFailure() {
        SdkClientException exception = SdkClientException.builder()
                .message("Unable to unmarshall error response (String value length exceeds the maximum allowed). "
                        + "Response Code: 400, Response Text: Bad Request")
                .build();

        BellaException result = AwsExceptionUtils.toBellaException(new CompletionException(exception));

        assertEquals(Integer.valueOf(400), result.getHttpCode());
        assertTrue(result instanceof BellaException.ChannelException);
    }

    @Test
    public void capturesHttpStatusBeforeSdkUnmarshalling() {
        AwsExceptionUtils.ResponseStatusCapture responseStatusCapture = new AwsExceptionUtils.ResponseStatusCapture();
        ExecutionAttributes executionAttributes = new ExecutionAttributes()
                .putAttribute(AwsExceptionUtils.responseStatusAttribute(), responseStatusCapture);
        Context.AfterTransmission context = mock(Context.AfterTransmission.class);
        when(context.httpResponse()).thenReturn(SdkHttpResponse.builder().statusCode(400).build());

        AwsExceptionUtils.responseStatusInterceptor().afterTransmission(context, executionAttributes);

        assertEquals(Integer.valueOf(400), responseStatusCapture.responseCode());
    }

    @Test
    public void preservesBedrockServiceStatusCode() {
        SdkServiceException exception = SdkServiceException.builder()
                .statusCode(429)
                .message("too many requests")
                .build();

        BellaException result = AwsExceptionUtils.toBellaException(exception);

        assertEquals(Integer.valueOf(429), result.getHttpCode());
    }

    @Test
    public void restoresCapturedStatusWhenSdkOnlyPropagatesParserCause() {
        RuntimeException exception = new RuntimeException("String value length exceeds the maximum allowed");

        BellaException result = AwsExceptionUtils.toBellaException(exception, 400);

        assertEquals(Integer.valueOf(400), result.getHttpCode());
        assertTrue(result instanceof BellaException.ChannelException);
    }

    @Test
    public void doesNotTreatClientErrorWithoutResponseCodeAsBadRequest() {
        SdkClientException exception = SdkClientException.builder()
                .message("Unable to execute HTTP request")
                .build();

        BellaException result = AwsExceptionUtils.toBellaException(exception);

        assertEquals(Integer.valueOf(500), result.getHttpCode());
    }

    @Test
    public void doesNotUseSuccessfulResponseCodeForSdkParseFailure() {
        SdkClientException exception = SdkClientException.builder()
                .message("Unable to unmarshall response. Response Code: 200, Response Text: OK")
                .build();

        BellaException result = AwsExceptionUtils.toBellaException(exception);

        assertEquals(Integer.valueOf(500), result.getHttpCode());
    }

    @Test
    public void mapsActualSdkErrorResponseUnmarshalFailureToBadRequest() throws Exception {
        String responseBody = "{\"message\":\"" + repeat('A', 20_000_001) + "\"}";
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().close();
            byte[] body = responseBody.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        AwsExceptionUtils.ResponseStatusCapture responseStatusCapture = new AwsExceptionUtils.ResponseStatusCapture();
        BedrockRuntimeClient client = BedrockRuntimeClient.builder()
                .endpointOverride(URI.create("http://localhost:" + server.getAddress().getPort()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .overrideConfiguration(config -> config.addExecutionInterceptor(AwsExceptionUtils.responseStatusInterceptor()))
                .build();
        try {
            client.invokeModel(InvokeModelRequest.builder()
                    .modelId("test")
                    .body(SdkBytes.fromUtf8String("{}"))
                    .overrideConfiguration(config -> config.putExecutionAttribute(
                            AwsExceptionUtils.responseStatusAttribute(), responseStatusCapture))
                    .build());
            fail("AWS SDK should reject the oversized error string");
        } catch (Exception exception) {
            BellaException result = AwsExceptionUtils.toBellaException(exception, responseStatusCapture.responseCode());
            assertEquals(Integer.valueOf(400), result.getHttpCode());
        } finally {
            client.close();
            server.stop(0);
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for(int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
