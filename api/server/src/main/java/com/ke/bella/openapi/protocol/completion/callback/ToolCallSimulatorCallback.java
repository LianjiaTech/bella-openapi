package com.ke.bella.openapi.protocol.completion.callback;

import org.apache.commons.lang3.StringUtils;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.simulation.FunctionCallContentBuffer;
import com.ke.bella.openapi.simulation.FunctionCallListener;
import com.ke.bella.openapi.simulation.SimulationHepler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ToolCallSimulatorCallback extends Callbacks.StreamCompletionCallbackNode {
    private final FunctionCallContentBuffer buffer;
    private final EndpointProcessData endpointProcessData;
    private boolean isOpen;

    public ToolCallSimulatorCallback(EndpointProcessData endpointProcessData) {
        this.endpointProcessData = endpointProcessData;
        this.buffer = new FunctionCallContentBuffer();
    }

    @Override
    public void onOpen() {
        try {
            TaskExecutor.submit(() -> {
                try {
                    SimulationHepler.parse(buffer, new FunctionCallListener() {
                        @Override
                        public void onMessage(StreamCompletionResponse msg) {
                            msg.setId(buffer.getLast().getId());
                            msg.setModel(buffer.getLast().getModel());
                            msg.setCreated(System.currentTimeMillis());
                            next.callback(msg);
                        }

                        @Override
                        public void onFinish() {
                            buffer.getLasts().forEach(e -> next.callback(e));
                            next.done();
                            next.finish();
                        }
                    });
                } catch (Throwable e) {
                    next.done();
                    next.finish();
                    log.warn("faild to parse function call, buffer: {}", buffer.toString(), e);
                }
            });
            isOpen = true;
        } finally {
            next.onOpen();
        }
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        if(!endpointProcessData.isFunctionCallSimulate()) {
            next.callback(msg);
            return;
        }
        String reasoning = msg.reasoning();
        if(StringUtils.isNotEmpty(reasoning)) {
            next.callback(msg);
        } else {
            buffer.append(msg);
        }
    }

    @Override
    public StreamCompletionResponse doCallback(StreamCompletionResponse msg) {
        return msg;
    }

    @Override
    public void done() {
        buffer.finish();
    }

    @Override
    public void finish() {
        buffer.finish();
    }

    @Override
    public void finish(ChannelException exception) {
        if(isOpen) {
            buffer.finish();
        } else {
            next.finish(exception);
        }
    }
}
