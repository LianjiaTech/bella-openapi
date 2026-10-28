package com.ke.bella.openapi.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.auth.AuthResult;
import com.ke.bella.openapi.auth.CredentialType;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.Interceptor;
import okhttp3.Request;

public class BellaInterceptorTest {

    @After
    public void clearContext() {
        BellaContext.clearAll();
        BellaInterceptor.configureServiceClient(null, null);
    }

    @Test
    public void injectsServiceClientCredentialForSameHostForwarding() throws Exception {
        BellaInterceptor.configureServiceClient("default-client", "service-credential-xxx");
        Request request = new Request.Builder()
                .url("http://localhost:8080/v1/chat/completions")
                .header("Authorization", "Bearer service-credential-xxx")
                .header(BellaContext.SERVICE_CLIENT_HEADER, "default-client")
                .header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER, "ak-user-001")
                .build();
        AuthResult authResult = AuthResult.builder()
                .credentialType(CredentialType.SERVICE_CLIENT)
                .serviceClientCode("default-client")
                .build();
        Map<String, Object> context = new HashMap<>();
        context.put("authResult", authResult);
        context.put("ak", com.ke.bella.openapi.apikey.ApikeyInfo.builder().code("ak-user-001").build());
        context.put("headers", new HashMap<String, String>());

        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any(Request.class))).thenAnswer(invocation -> {
            Request forwarded = invocation.getArgument(0);
            assertThat(forwarded.header("Authorization")).isEqualTo("Bearer service-credential-xxx");
            assertThat(forwarded.header(BellaContext.SERVICE_CLIENT_HEADER)).isEqualTo("default-client");
            assertThat(forwarded.header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER)).isEqualTo("ak-user-001");
            return null;
        });

        new BellaInterceptor("http://localhost:8080", context).intercept(chain);
    }

    @Test
    public void injectsServiceClientCredentialForGenericSameHostRequest() throws Exception {
        BellaInterceptor.configureServiceClient("default-client", "service-credential-xxx");
        Request request = new Request.Builder()
                .url("http://localhost:8080/v1/chat/completions")
                .header("Authorization", "Bearer service-credential-xxx")
                .build();
        Map<String, Object> context = new HashMap<>();
        context.put("authResult", AuthResult.builder()
                .credentialType(CredentialType.SERVICE_CLIENT)
                .serviceClientCode("default-client")
                .build());
        context.put("ak", com.ke.bella.openapi.apikey.ApikeyInfo.builder().code("ak-user-001").build());

        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any(Request.class))).thenAnswer(invocation -> {
            Request forwarded = invocation.getArgument(0);
            assertThat(forwarded.header("Authorization")).isEqualTo("Bearer service-credential-xxx");
            assertThat(forwarded.header(BellaContext.SERVICE_CLIENT_HEADER)).isEqualTo("default-client");
            assertThat(forwarded.header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER)).isEqualTo("ak-user-001");
            return null;
        });

        new BellaInterceptor("http://localhost:8080", context).intercept(chain);
        BellaInterceptor.configureServiceClient(null, null);
    }

    @Test
    public void preservesAuthorizationWhenServiceClientIsDisabled() throws Exception {
        BellaInterceptor.configureServiceClient("default-client", null);
        Request request = new Request.Builder()
                .url("http://localhost:8080/v1/chat/completions")
                .header("Authorization", "Bearer user-api-key")
                .build();
        Map<String, Object> context = new HashMap<>();
        context.put("authResult", AuthResult.builder()
                .credentialType(CredentialType.SERVICE_CLIENT)
                .build());
        context.put("ak", com.ke.bella.openapi.apikey.ApikeyInfo.builder().code("ak-user-001").build());
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any(Request.class))).thenAnswer(invocation -> {
            Request forwarded = invocation.getArgument(0);
            assertThat(forwarded.header("Authorization")).isEqualTo("Bearer user-api-key");
            assertThat(forwarded.header(BellaContext.SERVICE_CLIENT_HEADER)).isNull();
            return null;
        });

        new BellaInterceptor("http://localhost:8080", context).intercept(chain);
    }

    @Test
    public void removesServiceClientHeadersButPreservesExternalAuthorization() throws Exception {
        Request request = new Request.Builder()
                .url("https://supplier.example/v1/chat/completions")
                .header("Authorization", "Bearer provider-credential-xxx")
                .header(BellaContext.SERVICE_CLIENT_HEADER, "default-client")
                .header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER, "ak-user-001")
                .build();
        Map<String, Object> context = new HashMap<>();
        context.put("authResult", AuthResult.builder()
                .credentialType(CredentialType.SERVICE_CLIENT)
                .serviceClientCode("default-client")
                .build());
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any(Request.class))).thenAnswer(invocation -> {
            Request forwarded = invocation.getArgument(0);
            assertThat(forwarded.header("Authorization")).isEqualTo("Bearer provider-credential-xxx");
            assertThat(forwarded.header(BellaContext.SERVICE_CLIENT_HEADER)).isNull();
            assertThat(forwarded.header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER)).isNull();
            return null;
        });

        new BellaInterceptor("http://localhost:8080", context).intercept(chain);
    }

    @Test
    public void removesConfiguredServiceClientAuthorizationFromExternalRequest() throws Exception {
        BellaInterceptor.configureServiceClient("default-client", "service-credential-xxx");
        Request request = new Request.Builder()
                .url("https://supplier.example/v1/chat/completions")
                .header("Authorization", "Bearer service-credential-xxx")
                .build();
        Map<String, Object> context = new HashMap<>();
        context.put("authResult", AuthResult.builder().credentialType(CredentialType.SERVICE_CLIENT).build());
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any(Request.class))).thenAnswer(invocation -> {
            Request forwarded = invocation.getArgument(0);
            assertThat(forwarded.header("Authorization")).isNull();
            return null;
        });

        new BellaInterceptor("http://localhost:8080", context).intercept(chain);
    }

    @Test
    public void addsServiceClientHeadersForIndependentHost() throws Exception {
        BellaInterceptor.configureServiceClient("default-client", "service-credential-xxx");
        BellaContext.setAuthResult(AuthResult.builder()
                .credentialType(CredentialType.SERVICE_CLIENT)
                .apikeyInfo(com.ke.bella.openapi.apikey.ApikeyInfo.builder().code("ak-user-001").build())
                .build());
        Request.Builder requestBuilder = new Request.Builder()
                .url("https://job-queue.example/v1/queue/put");
        BellaInterceptor.addServiceClientHeaders(requestBuilder, BellaContext.snapshot());
        Request request = requestBuilder.build();
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(request);
        when(chain.proceed(any(Request.class))).thenAnswer(invocation -> {
            Request forwarded = invocation.getArgument(0);
            assertThat(forwarded.header("Authorization")).isEqualTo("Bearer service-credential-xxx");
            assertThat(forwarded.header(BellaContext.SERVICE_CLIENT_HEADER)).isEqualTo("default-client");
            assertThat(forwarded.header(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER)).isEqualTo("ak-user-001");
            return null;
        });

        new BellaInterceptor("https://openapi.example", BellaContext.snapshot()).intercept(chain);
    }

    @Test
    public void authSnapshotDoesNotSerializePresentedCredential() {
        BellaContext.setAuthResult(AuthResult.builder()
                .credentialType(CredentialType.SERVICE_CLIENT)
                .serviceClientCode("default-client")
                .build());

        String snapshot = JacksonUtils.serialize(BellaContext.snapshot());

        assertThat(snapshot).doesNotContain("service-credential-xxx");
        assertThat(snapshot).contains("default-client");
    }
}
