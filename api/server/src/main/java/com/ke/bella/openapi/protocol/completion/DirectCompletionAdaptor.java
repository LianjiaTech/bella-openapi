package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

/**
 * Direct mode completion adaptor that passes requests directly to channels
 * with minimal processing. This adaptor:
 * 1. Forwards InputStream directly to channel
 * 2. Transparently passes through channel responses
 * 3. Uses simplified request building without complex transformations
 * 4. Supports both streaming and non-streaming modes
 */
@Component("DirectCompletion")
public class DirectCompletionAdaptor implements CompletionAdaptorDelegator<DirectCompletionProperty> {

    private final Callbacks.SseEventConverter<StreamCompletionResponse> sseConverter = new Callbacks.DefaultSseConverter();

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, DirectCompletionProperty property, Callbacks.HttpDelegator delegator) {
        if (delegator != null) {
            return delegator.request(request, CompletionResponse.class, null);
        }

        Request httpRequest = buildDirectRequest(request, url, property);
        return HttpUtils.httpRequest(httpRequest, CompletionResponse.class);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, DirectCompletionProperty property,
                                  Callbacks.StreamCompletionCallback callback, Callbacks.StreamDelegator delegator) {
        // Use DirectStreamCompletionCallback wrapper for async processing
        DirectStreamCompletionCallback directCallback = new DirectStreamCompletionCallback(callback);
        CompletionSseListener listener = new CompletionSseListener(directCallback, sseConverter);

        if (delegator != null) {
            delegator.request(request, listener);
        } else {
            Request httpRequest = buildDirectRequest(request, url, property);
            HttpUtils.streamRequest(httpRequest, listener);
        }
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, DirectCompletionProperty property) {
        return completion(request, url, property, null);
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, DirectCompletionProperty property,
                                  Callbacks.StreamCompletionCallback callback) {
        streamCompletion(request, url, property, callback, null);
    }

    /**
     * Build direct request with minimal transformation.
     * Just serialize the request and forward to channel.
     */
    private Request buildDirectRequest(CompletionRequest request, String url, DirectCompletionProperty property) {
        // Set the deploy name (model) from property
        request.setModel(property.getDeployName());

        // Build request with authorization
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request)));

        // Add any extra headers from property
        if (property.getExtraHeaders() != null && !property.getExtraHeaders().isEmpty()) {
            property.getExtraHeaders().forEach(builder::addHeader);
        }

        return builder.build();
    }

    @Override
    public String getDescription() {
        return "Direct mode protocol - passthrough with minimal processing";
    }

    @Override
    public Class<?> getPropertyClass() {
        return DirectCompletionProperty.class;
    }
}
