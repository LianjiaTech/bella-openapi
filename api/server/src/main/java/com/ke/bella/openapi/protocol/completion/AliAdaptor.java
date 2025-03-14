package com.ke.bella.openapi.protocol.completion;

import java.util.List;

import com.ke.bella.openapi.protocol.completion.AliCompletionRequest;
import com.ke.bella.openapi.protocol.completion.AliCompletionResponse;
import com.ke.bella.openapi.protocol.completion.AliProperty;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.CompletionSseListener;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.Callbacks.StreamCompletionCallback;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@Component("AliCompletion")
public class AliAdaptor implements CompletionAdaptorDelegator<AliProperty> {
    @Override
    public String getDescription() {
        return "阿里协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AliProperty.class;
    }

    Callbacks.ChannelErrorCallback<AliCompletionResponse> errorCallback = (aliCompletionResponse, okhttpResponse) -> aliCompletionResponse.setHttpCode(okhttpResponse.code());

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, AliProperty property, Callbacks.HttpDelegator delegator) {
        AliCompletionRequest aliRequest = rewriteRequest(request, property);
        AliCompletionResponse response;
        if(delegator == null) {
            Request httpRequest = buildRequest(aliRequest, url, property);
            response = HttpUtils.httpRequest(httpRequest, AliCompletionResponse.class, errorCallback);
        } else {
            response = delegator.request(aliRequest, AliCompletionResponse.class, errorCallback);
        }
        return responseConvert(response);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, AliProperty property, StreamCompletionCallback callback,
            Callbacks.StreamDelegator delegator) {
        AliCompletionRequest aliRequest = rewriteRequest(request, property);
        CompletionSseListener sseListener = new CompletionSseListener(callback, sseConverter);
        if(delegator == null) {
            Request httpRequest = buildRequest(aliRequest, url, property);
            HttpUtils.streamRequest(httpRequest, sseListener);
        } else {
            delegator.request(aliRequest, sseListener);
        }
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, AliProperty property) {
        return completion(request, url, property, null);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, AliProperty property, StreamCompletionCallback callback) {
         streamCompletion(request, url, property, callback,null);

    }

    public AliCompletionRequest rewriteRequest(CompletionRequest request, AliProperty property) {
        AliCompletionRequest aliRequest = requestConvert(request);
        aliRequest.setModel(property.getDeployName());
        return aliRequest;
    }


    private Request buildRequest(AliCompletionRequest request, String url, AliProperty property) {
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(JacksonUtils.serialize(request),
                        MediaType.parse("application/json")));
        return builder.build();
    }

    private CompletionResponse.TokenUsage convertTokenUsage(AliCompletionResponse.AliUsage aliUsage) {
        if(aliUsage == null) {
            return null;
        }
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(aliUsage.getInputTokens() + aliUsage.getAudioTokens() + aliUsage.getImageTokens());
        usage.setCompletion_tokens(aliUsage.getOutTokens());
        usage.setTotal_tokens(usage.getPrompt_tokens() + usage.getCompletion_tokens());
        return usage;
    }

    private final Callbacks.SseEventConverter<StreamCompletionResponse> sseConverter = (id, event, msg) -> {
        AliCompletionResponse response = JacksonUtils.deserialize(msg, AliCompletionResponse.class);
        OpenapiResponse.OpenapiError openAIError = null;
        List<StreamCompletionResponse.Choice> choices = null;
        if(response.getOutput() == null) {
            openAIError = OpenapiResponse.OpenapiError.builder()
                    .type(response.getCode())
                    .message(response.getMessage())
                    .build();
            if(response.getHttpCode() != null && response.getHttpCode() > 299) {
                openAIError.setHttpCode(response.getHttpCode());
            }
        } else {
            choices = Lists.newArrayList(StreamCompletionResponse.Choice.builder()
                    .delta(response.getOutput().getChoices().get(0).getMessage())
                    .finish_reason(response.getOutput().getChoices().get(0).getFinish_reason())
                    .build());
        }
        return StreamCompletionResponse.builder()
                .created(DateTimeUtils.getCurrentSeconds())
                .choices(choices)
                .usage(convertTokenUsage(response.getUsage()))
                .error(openAIError)
                .build();
    };

    private CompletionResponse responseConvert(AliCompletionResponse response) {
        if(response == null) {
            return null;
        }
        OpenapiResponse.OpenapiError openAIError = null;
        List<CompletionResponse.Choice> choices = null;
        if(response.getHttpCode() != null && response.getHttpCode() > 299) {
            openAIError = OpenapiResponse.OpenapiError.builder()
                    .type(response.getCode())
                    .httpCode(response.getHttpCode())
                    .message(response.getMessage())
                    .build();
        } else {
            choices = response.getOutput().getChoices();
        }
        return CompletionResponse.builder()
                .id(response.getRequestId())
                .choices(choices)
                .created(DateTimeUtils.getCurrentSeconds())
                .usage(convertTokenUsage(response.getUsage()))
                .error(openAIError)
                .build();
    }

    private AliCompletionRequest requestConvert(CompletionRequest request) {
        AliCompletionRequest.AliCompletionInput input = AliCompletionRequest.AliCompletionInput.builder()
                .messages(request.getMessages())
                .build();
        AliCompletionRequest.AliCompletionParameters parameters = AliCompletionRequest.AliCompletionParameters.builder()
                .topP(request.getTop_p())
                .temperature(request.getTemperature())
                .seed(request.getSeed())
                .maxTokens(request.getMax_tokens())
                .stopString(request.getStop())
                .tools(request.getTools())
                .incremental_output(request.isStream())
                .build();
        return AliCompletionRequest.builder()
                .model(request.getModel())
                .input(input)
                .parameters(parameters).build();
    }
}
