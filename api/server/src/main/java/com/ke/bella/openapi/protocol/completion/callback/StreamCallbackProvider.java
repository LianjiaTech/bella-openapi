package com.ke.bella.openapi.protocol.completion.callback;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.message.StreamMessagesCallback;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class StreamCallbackProvider {
    public static Callbacks.StreamCompletionCallback provide(SseEmitter sse, EndpointProcessData processData, ApikeyInfo apikeyInfo,
            EndpointLogger logger, ISafetyCheckService.IChatSafetyCheckService safetyService, CompletionProperty property) {
        Callbacks.StreamCompletionCallbackNode root = new SplitReasoningCallback(property);
        root.addLast(new ToolCallSimulatorCallback(processData));
        root.addLast(new MergeReasoningCallback(property));
        root.addLast(new StreamCompletionCallback(sse, processData, apikeyInfo, logger, safetyService));
        return root;
    }

    public static Callbacks.StreamCompletionCallback provideForMessage(SseEmitter sse, EndpointProcessData processData, ApikeyInfo apikeyInfo,
            EndpointLogger logger, ISafetyCheckService.IChatSafetyCheckService safetyService, CompletionProperty property) {
        Callbacks.StreamCompletionCallbackNode root = new SplitReasoningCallback(property);
        root.addLast(new ToolCallSimulatorCallback(processData));
        root.addLast(new MergeReasoningCallback(property));
        root.addLast(new StreamMessagesCallback(sse, processData, apikeyInfo, logger, safetyService));
        return root;
    }
}
