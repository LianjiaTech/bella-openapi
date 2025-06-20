package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.AwsClientManager;
import com.ke.bella.openapi.protocol.completion.AwsMessageProperty;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@Component("AwsMessageMessage")
public class AwsMessageAdaptor implements MessageAdaptor<AwsMessageProperty> {

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
        request.setModel(null);
        request.setStream(null);
        if(request.getMaxTokens() == null) {
            request.setMaxTokens(property.getDefaultMaxToken());
        }
        request.setAnthropic_version(property.getAnthropicVersion());
        BedrockRuntimeClient client = AwsClientManager.client(property.getRegion(), url, property.getAuth().getApiKey(), property.getAuth().getSecret());
        try {
            InvokeModelResponse response = client.invokeModel(InvokeModelRequest.builder()
                    .body(SdkBytes.fromUtf8String(JacksonUtils.serialize(request)))
                    .modelId(property.getDeployName())
                    .build());
            MessageResponse messageResponse = JacksonUtils.deserialize(response.body().asByteArray(), MessageResponse.class);
            EndpointContext.getProcessData().setResponse(TransferToCompletionsUtils.convertResponse(messageResponse));
            return messageResponse;
        } catch (BedrockRuntimeException bedrockException) {
            throw ChannelException.fromResponse(bedrockException.statusCode(), bedrockException.getMessage());
        }
    }

    @Override
    public void streamMessages(MessageRequest request, String url, AwsMessageProperty property, Callbacks.StreamCompletionCallback callback) {
        request.setModel(null);
        request.setStream(null);
        if(request.getMaxTokens() == null) {
            request.setMaxTokens(property.getDefaultMaxToken());
        }
        request.setAnthropic_version(property.getAnthropicVersion());
        BedrockRuntimeAsyncClient client = AwsClientManager.asyncClient(property.getRegion(), url, property.getAuth().getApiKey(), property.getAuth().getSecret());
        InvokeModelWithResponseStreamRequest streamRequest = InvokeModelWithResponseStreamRequest.builder()
                .body(SdkBytes.fromUtf8String(JacksonUtils.serialize(request)))
                .modelId(property.getDeployName())
                .build();
        EndpointProcessData processData = EndpointContext.getProcessData();
        processData.setNativeSend(endpoint().equals(processData.getEndpoint()));
        AwsSseCompletionCallBack awsCallBack = new AwsSseCompletionCallBack(callback, processData.isNativeSend());
        InvokeModelWithResponseStreamResponseHandler handler = InvokeModelWithResponseStreamResponseHandler.builder()
                .subscriber(awsCallBack)
                .onComplete(awsCallBack)
                .onError(awsCallBack)
                .build();
        try {
            client.invokeModelWithResponseStream(streamRequest, handler);
        } catch (BedrockRuntimeException bedrockException) {
            throw ChannelException.fromResponse(bedrockException.statusCode(), bedrockException.getMessage());
        }
    }

    static class AwsSseCompletionCallBack implements InvokeModelWithResponseStreamResponseHandler.Visitor, Consumer<Throwable>, Runnable {
        public AwsSseCompletionCallBack(Callbacks.StreamCompletionCallback callback, boolean nativeSend) {
            this.callback = callback;
            this.nativeSend = nativeSend;
        }

        private final Callbacks.StreamCompletionCallback callback;
        private final Boolean nativeSend;
        private final AtomicInteger toolNum = new AtomicInteger(0);
        private boolean isFirst = true;
        private String model = "LLM";
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
            if(nativeSend) {
                callback.send(response);
            }
            if ("message_start".equals(response.getType()) && response.getMessage() != null) {
                model = response.getMessage().getModel();
                id = response.getMessage().getId();
                usage = response.getMessage().getUsage();
                return;
            }
            if("message_stop".equals(response.getType())) {
                callback.done();
                return;
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
            LOGGER.warn(throwable.getMessage(), throwable);
            if (throwable instanceof BedrockRuntimeException) {
                BedrockRuntimeException bedrockException = (BedrockRuntimeException) throwable;
                callback.finish(ChannelException.fromResponse(bedrockException.statusCode(), bedrockException.getMessage()));
                return;
            }
            callback.finish(ChannelException.fromException(throwable));
        }
    }
}
