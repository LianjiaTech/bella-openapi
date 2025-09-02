package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks.StreamCompletionCallback;
import com.ke.bella.openapi.protocol.completion.CompletionResponse.Choice;
import com.ke.bella.openapi.simulation.SimulationHepler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ToolCallSimulator<T extends CompletionProperty> implements CompletionAdaptor<T> {

    private final CompletionAdaptor<T> delegator;
    private final EndpointProcessData processData;

    public ToolCallSimulator(CompletionAdaptor<T> adaptor, EndpointProcessData processData) {
        this.delegator = adaptor;
        this.processData = processData;
    }

    @Override
    public String getDescription() {
        return delegator.getDescription();
    }

    @Override
    public Class<?> getPropertyClass() {
        return delegator.getPropertyClass();
    }

    @Override
    public CompletionResponse completion(CompletionRequest request, String url, T property) {
        if(property.isFunctionCallSimulate()) {
            CompletionRequest req = SimulationHepler.rewrite(request);
            if(req == null) {
                return delegator.completion(request, url, property);
            } else {
                processData.setFunctionCallSimulate(true);
                CompletionResponse resp = delegator.completion(req, url, property);
                try {
                    // 解析为function call，替换第一个choice
                    Choice choice = SimulationHepler.parse(resp.reasoning(), resp.content());
                    choice.setFinish_reason(resp.finishReason());
                    resp.getChoices().set(0, choice);
                } catch (Exception e) {
                    log.info(resp.content(), e);
                }
                return resp;
            }
        } else {
            return delegator.completion(request, url, property);
        }
    }

    @Override
    public void streamCompletion(CompletionRequest request, String url, T property, StreamCompletionCallback callback) {
        CompletionRequest req = SimulationHepler.rewrite(request);
        if(req == null) {
            delegator.streamCompletion(request, url, property, callback);
        } else {
            processData.setFunctionCallSimulate(true);
            delegator.streamCompletion(req, url, property, callback);
        }
    }
}
