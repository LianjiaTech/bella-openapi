package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.Callbacks.StreamCompletionCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;

import java.util.function.Consumer;

@Slf4j
@Component("AwsCompletion")
public class AwsAdaptor implements CompletionAdaptor<AwsProperty> {

    @Override
    public String getDescription() {
        return "亚马逊协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AwsProperty.class;
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, AwsProperty property) {
        request.setModel(property.deployName);
        ConverseRequest awsRequest =  AwsCompletionConverter.convert2AwsRequest(request, property);
        BedrockRuntimeClient client = AwsClientManager.client(property.region, url, property.auth.getApiKey(), property.auth.getSecret());
        try {
            ConverseResponse response = client.converse(awsRequest);
            return AwsCompletionConverter.convert2OpenAIResponse(response);
        } catch (BedrockRuntimeException bedrockException) {
            throw ChannelException.fromResponse(bedrockException.statusCode(), bedrockException.getMessage());
        }
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, AwsProperty property,
            StreamCompletionCallback callback) {
        request.setModel(property.deployName);
        ConverseStreamRequest awsRequest = AwsCompletionConverter.convert2AwsStreamRequest(request, property);
        BedrockRuntimeAsyncClient client = AwsClientManager.asyncClient(property.region, url, property.auth.getApiKey(), property.auth.getSecret());
        AwsSseCompletionCallBack awsCallBack = new AwsSseCompletionCallBack(callback);
        try {
            client.converseStream(awsRequest, ConverseStreamResponseHandler.builder()
                    .subscriber(awsCallBack)
                    .onError(awsCallBack)
                    .onComplete(awsCallBack)
                    .build());
        } catch (Exception bedrockException) {
            LOGGER.info("sse异常,{}", bedrockException.getMessage());
        }
    }

    static class AwsSseCompletionCallBack implements ConverseStreamResponseHandler.Visitor, Consumer<Throwable>, Runnable {
        public AwsSseCompletionCallBack(StreamCompletionCallback callback) {
            this.callback = callback;
        }

        private final Callbacks.StreamCompletionCallback callback;
        private boolean isFirst = true;
        private int toolNum = 0;

        @Override
        public void visitContentBlockStart(ContentBlockStartEvent event) {
            if(isFirst) {
                callback.onOpen();
                isFirst = false;
            }
            if(event.start().toolUse() != null) {
                toolNum += 1;
            }
            StreamCompletionResponse response = AwsCompletionConverter.convert2OpenAIStreamResponse(event.start(), Math.max(0, toolNum -1));
            callback.callback(response);
        }

        @Override
        public void visitContentBlockDelta(ContentBlockDeltaEvent event) {
            if(isFirst) {
                callback.onOpen();
                isFirst = false;
            }
            StreamCompletionResponse response = AwsCompletionConverter.convert2OpenAIStreamResponse(event.delta(), Math.max(0, toolNum -1));
            callback.callback(response);
        }


        @Override
        public void visitMetadata(ConverseStreamMetadataEvent event) {
            StreamCompletionResponse response = AwsCompletionConverter.convertTo2OpenAIStreamResponse(event.usage());
            callback.callback(response);
            callback.done();
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
