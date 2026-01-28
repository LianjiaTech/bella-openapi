package com.ke.bella.openapi.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.BellaStreamCallback;
import com.ke.bella.openapi.protocol.BellaWebSocketListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.request.BellaInterceptor;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.internal.Util;
import okhttp3.sse.EventSources;

/**
 * Author: Stan Sai Date: 2024/8/14 12:09 description:
 */
@Slf4j
public class HttpUtils {

    /**
     * -- GETTER --
     * 获取连接池实例，用于监控
     */
    @Getter
    private static final ConnectionPool connectionPool = new ConnectionPool(200, 5, TimeUnit.MINUTES);

    @Getter
    private static final ThreadPoolExecutor executorService = new ThreadPoolExecutor(
            0,
            Integer.MAX_VALUE,
            60, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            Util.threadFactory("OkHttp Dispatcher", false),
            new ThreadPoolExecutor.CallerRunsPolicy());

    private static final int defaultConnectionTimeout = 30;
    private static final int defaultReadTimeout = 300;

    public static OkHttpClient defaultOkhttpClient() {
        OkHttpClient.Builder builder = clientBuilder()
                .connectTimeout(defaultConnectionTimeout, TimeUnit.SECONDS)
                .readTimeout(defaultReadTimeout, TimeUnit.SECONDS);
        return builder.build();
    }

    @Setter
    private static String openapiHost;

    private static OkHttpClient.Builder clientBuilder() {
        Dispatcher dispatcher = new Dispatcher(executorService);
        dispatcher.setMaxRequests(2000);
        dispatcher.setMaxRequestsPerHost(500);
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .proxySelector(ProxyUtils.getProxySelector())
                .connectionPool(connectionPool)
                .dispatcher(dispatcher);

        builder.addInterceptor(new BellaInterceptor(openapiHost, BellaContext.snapshot()));

        return builder;
    }

    public static Response httpRequest(Request request, int connectionTimeout, int readTimeout) throws IOException {
        return httpRequest(request, connectionTimeout, readTimeout, null);
    }

    public static Response httpRequest(Request request, int connectionTimeout, int readTimeout, Interceptor interceptor) throws IOException {
        OkHttpClient.Builder builder = clientBuilder()
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS);

        if(interceptor != null) {
            builder.addInterceptor(interceptor);
        }

        return builder.build().newCall(request).execute();
    }

    public static Response httpRequest(Request request) throws IOException {
        return httpRequest(request, defaultConnectionTimeout, defaultReadTimeout);
    }

    public static void streamRequest(Request request, BellaStreamCallback callback) {
        streamRequest(request, callback, defaultConnectionTimeout, defaultReadTimeout, null);
    }

    public static void streamRequest(Request request, BellaEventSourceListener listener) {
        streamRequest(request, listener, defaultConnectionTimeout, defaultReadTimeout, null);
    }

    public static void streamRequest(Request request, BellaStreamCallback callback, int connectionTimeout, int readTimeout) {
        streamRequest(request, callback, connectionTimeout, readTimeout, null);
    }

    public static void streamRequest(Request request, BellaEventSourceListener listener, int connectionTimeout, int readTimeout) {
        streamRequest(request, listener, connectionTimeout, readTimeout, null);
    }

    // Stream request methods with optional Interceptor support
    public static void streamRequest(Request request, BellaStreamCallback callback, Interceptor interceptor) {
        streamRequest(request, callback, defaultConnectionTimeout, defaultReadTimeout, interceptor);
    }

    public static void streamRequest(Request request, BellaEventSourceListener listener, Interceptor interceptor) {
        streamRequest(request, listener, defaultConnectionTimeout, defaultReadTimeout, interceptor);
    }

    // Core implementation methods with optional Interceptor
    public static void streamRequest(Request request, BellaStreamCallback callback, int connectionTimeout, int readTimeout, Interceptor interceptor) {
        CompletableFuture<?> future = new CompletableFuture<>();
        callback.setConnectionInitFuture(future);

        OkHttpClient.Builder builder = clientBuilder()
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS);

        if(interceptor != null) {
            builder.addInterceptor(interceptor);
        }

        builder.build().newCall(request).enqueue(callback);
        try {
            future.get();
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            if(e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    public static void streamRequest(Request request, BellaEventSourceListener listener, int connectionTimeout, int readTimeout,
            Interceptor interceptor) {
        CompletableFuture<?> future = new CompletableFuture<>();
        listener.setConnectionInitFuture(future);

        OkHttpClient.Builder builder = clientBuilder()
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS);

        if(interceptor != null) {
            builder.addInterceptor(interceptor);
        }

        EventSources.createFactory(builder.build()).newEventSource(request, listener);
        try {
            future.get();
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            if(e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    public static <T> T httpRequest(Request request, Class<T> clazz) {
        return httpRequest(request, clazz, null, defaultConnectionTimeout, defaultReadTimeout, null);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference) {
        return httpRequest(request, typeReference, null, defaultConnectionTimeout, defaultReadTimeout, null);
    }

    public static <T> T httpRequest(Request request, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
        return httpRequest(request, clazz, errorCallback, defaultConnectionTimeout, defaultReadTimeout, null);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference, Callbacks.ChannelErrorCallback<T> errorCallback) {
        return httpRequest(request, typeReference, errorCallback, defaultConnectionTimeout, defaultReadTimeout, null);
    }

    public static <T> T httpRequest(Request request, Class<T> clazz, int connectionTimeout, int readTimeout) {
        return httpRequest(request, clazz, null, connectionTimeout, readTimeout, null);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference, int connectionTimeout, int readTimeout) {
        return httpRequest(request, typeReference, null, connectionTimeout, readTimeout, null);
    }

    public static <T> T httpRequest(Request request, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback, int connectionTimeout,
            int readTimeout) {
        return httpRequest(request, clazz, errorCallback, connectionTimeout, readTimeout, null);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference, Callbacks.ChannelErrorCallback<T> errorCallback,
            int connectionTimeout, int readTimeout) {
        return httpRequest(request, typeReference, errorCallback, connectionTimeout, readTimeout, null);
    }

    // Methods with optional Interceptor support
    public static <T> T httpRequest(Request request, Class<T> clazz, Interceptor interceptor) {
        return httpRequest(request, clazz, null, defaultConnectionTimeout, defaultReadTimeout, interceptor);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference, Interceptor interceptor) {
        return httpRequest(request, typeReference, null, defaultConnectionTimeout, defaultReadTimeout, interceptor);
    }

    public static <T> T httpRequest(Request request, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback, Interceptor interceptor) {
        return httpRequest(request, clazz, errorCallback, defaultConnectionTimeout, defaultReadTimeout, interceptor);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference, Callbacks.ChannelErrorCallback<T> errorCallback,
            Interceptor interceptor) {
        return httpRequest(request, typeReference, errorCallback, defaultConnectionTimeout, defaultReadTimeout, interceptor);
    }

    // Core implementation methods with optional Interceptor
    public static <T> T httpRequest(Request request, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback, int connectionTimeout,
            int readTimeout, Interceptor interceptor) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, clazz), errorCallback, connectionTimeout, readTimeout, interceptor);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference, Callbacks.ChannelErrorCallback<T> errorCallback,
            int connectionTimeout, int readTimeout, Interceptor interceptor) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, typeReference), errorCallback, connectionTimeout, readTimeout,
                interceptor);
    }

    private static <T> T doHttpRequest(Request request, Function<byte[], T> responseConvert, Callbacks.ChannelErrorCallback<T> errorCallback,
            int connectionTimeout, int readTimeout, Interceptor interceptor) {
        try (Response response = HttpUtils.httpRequest(request, connectionTimeout, readTimeout, interceptor)) {
            T result = null;
            if(response.body() != null) {
                result = responseConvert.apply(response.body().bytes());
            }
            if(response.code() > 299) {
                if(result == null) {
                    if(response.code() > 499 && response.code() < 600) {
                        String message = "供应商返回：code: " + response.code() + " message: " + response.message();
                        throw new ChannelException.OpenAIException(503, "channel_error", message);
                    }
                    throw new ChannelException.OpenAIException(response.code(), "channel_error", response.message());
                } else {
                    if(errorCallback != null) {
                        errorCallback.callback(result, response);
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw ChannelException.fromException(e);
        }
    }

    public static byte[] doHttpRequest(Request request) {
        try (Response response = HttpUtils.httpRequest(request)) {
            byte[] bodyBytes = null;
            if(response.body() != null) {
                bodyBytes = response.body().bytes();
            }

            if(!response.isSuccessful()) {
                throw new IllegalStateException(String.format("failed to do http request, code: %s, message: %s ",
                        response.code(),
                        Optional.ofNullable(bodyBytes).map(String::new).orElse(null)));
            } else {
                return bodyBytes;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void doHttpRequest(Request request, File output) {
        try (Response response = HttpUtils.httpRequest(request)) {
            if(!response.isSuccessful()) {
                throw new IllegalStateException(String.format("failed to do http request, code: %s", response.code()));
            }

            ResponseBody body = response.body();
            if(body != null) {
                try (InputStream inputStream = body.byteStream()) {
                    Files.copy(inputStream, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 当且仅当http code为2xx时进行反序列化
     *
     * @param request
     * @param reference
     *
     * @return
     *
     * @param <T>
     */
    public static <T> T doHttpRequest(Request request, TypeReference<T> reference) {
        try (Response response = HttpUtils.httpRequest(request)) {
            String bodyStr = null;
            if(response.body() != null) {
                bodyStr = response.body().string();
            }

            if(!response.isSuccessful()) {
                throw new IllegalStateException(String.format("failed to do http request, code: %s, message: %s ",
                        response.code(),
                        Optional.ofNullable(bodyStr).map(String::new).orElse(null)));
            } else {
                return JacksonUtils.deserialize(bodyStr, reference);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static WebSocket websocketRequest(Request request, BellaWebSocketListener listener) {
        CompletableFuture<?> future = new CompletableFuture<>();
        listener.setConnectionInitFuture(future);
        WebSocket webSocket = defaultOkhttpClient().newWebSocket(request, listener);
        try {
            future.get();
            return webSocket;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interruptedException);
        } catch (ExecutionException e) {
            if(e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    public static InputStream downloadStream(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        Response response = httpRequest(request, defaultConnectionTimeout, defaultReadTimeout);

        if(!response.isSuccessful()) {
            String errorMsg = String.format("Failed to download from URL: %s, code: %d", url, response.code());
            response.close();
            throw new IOException(errorMsg);
        }

        ResponseBody body = response.body();
        if(body == null) {
            response.close();
            throw new IOException("Response body is null for URL: " + url);
        }

        return body.byteStream();
    }
}
