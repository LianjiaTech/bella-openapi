package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.AwsClientManager;
import com.ke.bella.openapi.protocol.completion.AwsMessageProperty;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.PayloadPart;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@Component("AwsMessageMessage")
public class AwsMessageAdaptor implements MessageDelegator<AwsMessageProperty> {

    @Override
    public String getDescription() {
        return "亚马逊Message API协议版本";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AwsMessageProperty.class;
    }

    @Override
    public MessageResponse createMessages(MessageRequest request, String url, AwsMessageProperty property) {
        return createMessages(request, url, property, null);
    }

    @Override
    public MessageResponse createMessages(MessageRequest request, String url, AwsMessageProperty property,
            Callbacks.HttpDelegator delegator) {
        if(request.getMaxTokens() == null) {
            request.setMaxTokens(property.getDefaultMaxToken());
        }
        request.setAnthropic_version(property.getAnthropicVersion());

        if(delegator != null) {
            return delegator.request(request, MessageResponse.class, (errorResponse, res) -> {
                if(errorResponse != null && errorResponse.getError() != null) {
                    errorResponse.getError().setHttpCode(res.code());
                }
            });
        }

        String model = request.getModel();
        Boolean stream = request.getStream();
        request.setModel(null);
        request.setStream(null);

        // 序列化请求并立即清理大型数据以释放内存
        byte[] requestBytes = JacksonUtils.toByte(request);
        request.setModel(model);
        request.setStream(stream);
        clearLargeData(request);

        validateProxyUrl(property.getProxyUrl());
        BedrockRuntimeClient client = AwsClientManager.client(property.getRegion(), url, property.getAuth().getApiKey(),
                property.getAuth().getSecret(), property.getProxyUrl());
        try {
            InvokeModelRequest.Builder awsRequestBuilder = InvokeModelRequest.builder()
                    .body(SdkBytes.fromByteArray(requestBytes))
                    .modelId(property.getDeployName());
            String requestMetadata = requestMetadataHeader();
            if(StringUtils.isNotBlank(requestMetadata)) {
                awsRequestBuilder.overrideConfiguration(config -> config.putHeader("X-Amzn-Bedrock-Request-Metadata",
                        requestMetadata));
            }
            InvokeModelResponse response = client.invokeModel(awsRequestBuilder.build());
            MessageResponse messageResponse = JacksonUtils.deserialize(response.body().asByteArray(), MessageResponse.class);
            messageResponse.setModel(model);
            EndpointContext.getProcessData().setResponse(TransferToCompletionsUtils.convertResponse(messageResponse));
            return messageResponse;
        } catch (BedrockRuntimeException bedrockException) {
            throw new BellaException.ChannelException(bedrockException.statusCode(), bedrockException.getMessage());
        }
    }

    @Override
    public void streamMessages(MessageRequest request, String url, AwsMessageProperty property, Callbacks.StreamCompletionCallback callback) {
        streamMessages(request, url, property, callback, null);
    }

    @Override
    public void streamMessages(MessageRequest request, String url, AwsMessageProperty property, Callbacks.StreamCompletionCallback callback,
            Callbacks.StreamDelegator delegator) {
        EndpointProcessData processData = EndpointContext.getProcessData();
        boolean nativeSend = endpoint().equals(processData.getEndpoint());
        processData.setNativeSend(nativeSend);
        if(request.getMaxTokens() == null) {
            request.setMaxTokens(property.getDefaultMaxToken());
        }
        request.setAnthropic_version(property.getAnthropicVersion());

        String model = request.getModel();
        if(delegator != null) {
            delegator.request(request, new AwsQueueSseListener(callback, model, nativeSend));
            return;
        }

        Boolean stream = request.getStream();
        request.setModel(null);
        request.setStream(null);

        // 序列化请求并立即清理大型数据以释放内存
        byte[] requestBytes = JacksonUtils.toByte(request);
        clearLargeData(request);
        request.setModel(model);
        request.setStream(stream);
        validateProxyUrl(property.getProxyUrl());
        BedrockRuntimeAsyncClient client = AwsClientManager.asyncClient(property.getRegion(), url, property.getAuth().getApiKey(),
                property.getAuth().getSecret(), property.getProxyUrl());
        InvokeModelWithResponseStreamRequest.Builder awsRequestBuilder = InvokeModelWithResponseStreamRequest.builder()
                .body(SdkBytes.fromByteArray(requestBytes))
                .modelId(property.getDeployName());
        String requestMetadata = requestMetadataHeader();
        if(StringUtils.isNotBlank(requestMetadata)) {
            awsRequestBuilder.overrideConfiguration(config -> config.putHeader("X-Amzn-Bedrock-Request-Metadata",
                    requestMetadata));
        }
        InvokeModelWithResponseStreamRequest streamRequest = awsRequestBuilder.build();
        AwsSseCompletionCallBack awsCallBack = new AwsSseCompletionCallBack(callback, nativeSend, model);
        InvokeModelWithResponseStreamResponseHandler handler = InvokeModelWithResponseStreamResponseHandler.builder()
                .subscriber(awsCallBack)
                .onComplete(awsCallBack)
                .onError(awsCallBack)
                .build();
        try {
            client.invokeModelWithResponseStream(streamRequest, handler);
        } catch (BedrockRuntimeException bedrockException) {
            throw new BellaException.ChannelException(bedrockException.statusCode(), bedrockException.getMessage());
        }
    }

    private void validateProxyUrl(String proxyUrl) {
        if(StringUtils.isNotEmpty(proxyUrl)
                && (proxyUrl.startsWith("socks5://") || proxyUrl.startsWith("socks://"))) {
            throw new BellaException.ChannelException(400,
                    "AWS Bedrock adapter does not support SOCKS5 proxy. Please use HTTP/HTTPS proxy instead.");
        }
    }

    private String requestMetadataHeader() {
        Map<String, String> requestMetadata = new HashMap<>();
        String traceId = BellaContext.getTraceId();
        if(StringUtils.isNotBlank(traceId)) {
            requestMetadata.put("traceId", StringUtils.left(traceId, 256));
        }
        String requestId = BellaContext.getRequestId();
        if(StringUtils.isNotBlank(requestId)) {
            requestMetadata.put("requestId", StringUtils.left(requestId, 256));
        }
        return requestMetadata.isEmpty() ? null : JacksonUtils.serialize(requestMetadata);
    }

    static class AwsSseCompletionCallBack implements InvokeModelWithResponseStreamResponseHandler.Visitor, Consumer<Throwable>, Runnable {
        public AwsSseCompletionCallBack(Callbacks.StreamCompletionCallback callback, boolean nativeSend, String model) {
            this.callback = callback;
            this.nativeSend = nativeSend;
            this.model = model;
        }

        private final Callbacks.StreamCompletionCallback callback;
        private final Boolean nativeSend;
        private String model;
        private final AtomicInteger toolNum = new AtomicInteger(0);
        private boolean isFirst = true;
        private String id = UUID.randomUUID().toString();
        private MessageResponse.Usage usage;

        @Override
        public void visitChunk(PayloadPart event) {
            if(isFirst) {
                callback.onOpen();
                isFirst = false;
            }
            StreamMessageResponse response = JacksonUtils.deserialize(event.bytes().asByteArray(), StreamMessageResponse.class);
            if(response == null) {
                return;
            }
            if("message_start".equals(response.getType()) && response.getMessage() != null) {
                model = response.getMessage().getModel();
                id = response.getMessage().getId();
                usage = response.getMessage().getUsage();
                if(nativeSend) {
                    callback.send(response);
                }
                return;
            }
            if("message_stop".equals(response.getType())) {
                if(nativeSend) {
                    callback.send(response);
                }
                callback.done();
                return;
            }
            // nativeSend 和 callback 两条路并行：nativeSend 透传原始 SSE，callback 填充 buffer 用于日志计费
            if(nativeSend) {
                // message_delta 事件的 usage 处理：
                // - 普通场景：上游 usage 仅含 output_tokens（inputTokens 反序列化为 0），
                //   需从 message_start 缓存中补入 input_tokens 及 cache_* 信息，
                //   使客户端可从最后一个 usage 事件获取完整 token 统计。
                // - tool_use 场景（如 web_search）：上游已在 usage 中提供完整 input_tokens（> 0），
                //   直接透传，避免用 message_start 的旧值覆盖最新的 token 计数。
                if("message_delta".equals(response.getType()) && response.getUsage() != null
                        && usage != null && response.getUsage().getInputTokens() == 0) {
                    // TODO: 适配 usage 中的 server_tool_use 信息（如 web_search_requests），
                    //       当前 StreamUsage 尚未定义该字段，补齐时也未携带，需后续扩展。
                    StreamMessageResponse.StreamUsage patchedUsage = StreamMessageResponse.StreamUsage.builder()
                            .inputTokens(usage.getInputTokens())
                            .outputTokens(response.getUsage().getOutputTokens())
                            .cacheCreationInputTokens(usage.getCacheCreationInputTokens())
                            .cacheReadInputTokens(usage.getCacheReadInputTokens())
                            .build();
                    callback.send(response.toBuilder().usage(patchedUsage).build());
                } else {
                    callback.send(response);
                }
            }
            StreamCompletionResponse openaiResponse = TransferToCompletionsUtils.convertStreamResponse(response, model, id, toolNum, usage);
            if(openaiResponse == null) {
                return;
            }
            callback.callback(openaiResponse);
        }

        @Override
        public void run() {
            callback.finish();
        }

        @Override
        public void accept(Throwable throwable) {
            log.warn(throwable.getMessage(), throwable);
            Throwable cause = throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null
                    ? throwable.getCause() : throwable;
            if(cause instanceof BedrockRuntimeException) {
                BedrockRuntimeException bedrockException = (BedrockRuntimeException) cause;
                callback.finish(new BellaException.ChannelException(bedrockException.statusCode(), bedrockException.getMessage()));
                return;
            }
            callback.finish(BellaException.fromException(cause));
        }
    }

    private static class AwsQueueSseListener extends BellaEventSourceListener {
        private final AwsSseCompletionCallBack awsCallback;

        AwsQueueSseListener(Callbacks.StreamCompletionCallback callback, String model, boolean nativeSend) {
            this.awsCallback = new AwsSseCompletionCallBack(callback, nativeSend, model);
        }

        @Override
        public void onEvent(EventSource eventSource, String id, String type, String msg) {
            PayloadPart payloadPart = PayloadPart.builder()
                    .bytes(SdkBytes.fromUtf8String(msg))
                    .build();
            awsCallback.visitChunk(payloadPart);
        }

        @Override
        public void onClosed(EventSource eventSource) {
            awsCallback.run();
        }

        @Override
        public void onFailure(EventSource eventSource, Throwable t, Response response) {
            BellaException exception = convertToException(t, response);
            if(connectionInitFuture.isDone()) {
                awsCallback.accept(exception);
            } else {
                connectionInitFuture.completeExceptionally(exception);
            }
        }

        private BellaException convertToException(Throwable t, Response response) {
            if(t != null) {
                return BellaException.fromException(t);
            }
            if(response != null) {
                return BellaException.fromResponse(response.code(), response.message());
            }
            return BellaException.fromException(new IllegalStateException("SSE request failed without response"));
        }
    }
}
