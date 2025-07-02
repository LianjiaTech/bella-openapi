package com.ke.bella.openapi.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.BellaStreamCallback;
import com.ke.bella.openapi.protocol.BellaWebSocketListener;
import com.ke.bella.openapi.protocol.Callbacks;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.internal.Util;
import okhttp3.sse.EventSources;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Author: Stan Sai Date: 2024/8/14 12:09 description:
 */
public class HttpUtils {
    private static final ConnectionPool connectionPool = new ConnectionPool(1500, 5, TimeUnit.MINUTES);
    private static final ExecutorService executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", false));
    private static final int defaultConnectionTimeout = 120;
    private static final int defaultReadTimeout = 300;
    private static final OkHttpClient.Builder clientBuilder = clientBuilder();
    public static OkHttpClient defaultOkhttpClient() {
        OkHttpClient.Builder builder = clientBuilder()
                .connectTimeout(defaultConnectionTimeout, TimeUnit.SECONDS)
                .readTimeout(defaultReadTimeout, TimeUnit.SECONDS);
        return builder.build();
    }
    private static OkHttpClient.Builder clientBuilder() {
        Dispatcher dispatcher = new Dispatcher(executorService);
        dispatcher.setMaxRequests(2000);
        dispatcher.setMaxRequestsPerHost(500);
        return new OkHttpClient.Builder()
                .proxySelector(ProxyUtils.getProxySelector())
                .connectionPool(connectionPool)
                .dispatcher(dispatcher)
                .pingInterval(30, TimeUnit.SECONDS);
    }

    public static Response httpRequest(Request request, int connectionTimeout, int readTimeout) throws IOException {
        return clientBuilder.connectTimeout(connectionTimeout, TimeUnit.SECONDS).readTimeout(readTimeout, TimeUnit.SECONDS)
                .build().newCall(request).execute();
    }

    public static Response httpRequest(Request request) throws IOException {
        return httpRequest(request, defaultConnectionTimeout, defaultReadTimeout);
    }

    public static void streamRequest(Request request, BellaStreamCallback callback) {
        streamRequest(request, callback, defaultConnectionTimeout, defaultReadTimeout);
    }

    public static void streamRequest(Request request, BellaEventSourceListener listener) {
        streamRequest(request, listener, defaultConnectionTimeout, defaultReadTimeout);
    }

    public static void streamRequest(Request request, BellaStreamCallback callback, int connectionTimeout, int readTimeout) {
        CompletableFuture<?> future = new CompletableFuture<>();
        callback.setConnectionInitFuture(future);
        clientBuilder.connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS).build().newCall(request).enqueue(callback);
        try {
            future.get();
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
            Thread.currentThread().interrupt();
        }  catch (ExecutionException e) {
            if(e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    public static void streamRequest(Request request, BellaEventSourceListener listener, int connectionTimeout, int readTimeout) {
        CompletableFuture<?> future = new CompletableFuture<>();
        listener.setConnectionInitFuture(future);
        EventSources.createFactory(clientBuilder.connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS).build()).newEventSource(request, listener);
        try {
            future.get();
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
            Thread.currentThread().interrupt();
        }  catch (ExecutionException e) {
            if(e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    public static <T> T httpRequest(Request request, Class<T> clazz) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, clazz), null, defaultConnectionTimeout, defaultReadTimeout);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, typeReference), null, defaultConnectionTimeout, defaultReadTimeout);
    }

    public static <T> T httpRequest(Request request, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, clazz), errorCallback, defaultConnectionTimeout, defaultReadTimeout);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference, Callbacks.ChannelErrorCallback<T> errorCallback) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, typeReference), errorCallback, defaultConnectionTimeout, defaultReadTimeout);
    }

    public static <T> T httpRequest(Request request, Class<T> clazz, int connectionTimeout, int readTimeout) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, clazz), null, connectionTimeout, readTimeout);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference, int connectionTimeout, int readTimeout) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, typeReference), null, connectionTimeout, readTimeout);
    }

    public static <T> T httpRequest(Request request, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback, int connectionTimeout, int readTimeout) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, clazz), errorCallback, connectionTimeout, readTimeout);
    }

    public static <T> T httpRequest(Request request, TypeReference<T> typeReference, Callbacks.ChannelErrorCallback<T> errorCallback, int connectionTimeout, int readTimeout) {
        return doHttpRequest(request, bytes -> JacksonUtils.deserialize(bytes, typeReference), errorCallback, connectionTimeout, readTimeout);
    }

    private static  <T> T doHttpRequest(Request request, Function<byte[], T> responseConvert, Callbacks.ChannelErrorCallback<T> errorCallback, int connectionTimeout, int readTimeout) {
        T result = null;
        try {
            Response response = HttpUtils.httpRequest(request, connectionTimeout, readTimeout);
            if(response.body() != null) {
                result = responseConvert.apply(response.body().bytes());
            }
            if(response.code() > 299) {
                if(result == null) {
                    if(response.code() > 499 && response.code() < 600) {
                        String message = "供应商返回：code: " + response.code() + " message: " + response.message();
                        throw ChannelException.fromResponse(503, message);
                    }
                    throw ChannelException.fromResponse(response.code(), response.message());
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
        Response response = null;
        ResponseBody body = null;
        try {
            response = HttpUtils.httpRequest(request);
            body = response.body();

            byte[] bodyBytes = null;
            if(body != null) {
                bodyBytes = body.bytes();
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
        } finally {
            if(response != null) {
                response.close();
            }
            if(body != null) {
                body.close();
            }
        }
    }

    public static void doHttpRequest(Request request, File output) {
        Response response = null;
        ResponseBody body = null;
        try {
            response = HttpUtils.httpRequest(request);

            if(!response.isSuccessful()) {
                throw new IllegalStateException(String.format("failed to do http request, code: %s", response.code()));
            }
            body = response.body();

            if(body != null) {
                InputStream stream = body.byteStream();
                try (FileOutputStream fos = new FileOutputStream(output);
                        ReadableByteChannel inputChannel = Channels.newChannel(stream);
                        FileChannel outputChannel = fos.getChannel()) {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
                    while (inputChannel.read(buffer) != -1) {
                        buffer.flip();
                        outputChannel.write(buffer);
                        buffer.clear();
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if(response != null) {
                response.close();
            }
            if(body != null) {
                body.close();
            }
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
        Response response = null;
        ResponseBody body = null;
        try {
            response = HttpUtils.httpRequest(request);
            body = response.body();

            String bodyStr = null;
            if(body != null) {
                bodyStr = body.string();
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
        } finally {
            if(response != null) {
                response.close();
            }
            if(body != null) {
                body.close();
            }
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
        }  catch (ExecutionException e) {
            if(e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }
}
