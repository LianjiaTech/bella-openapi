package com.ke.bella.openapi.protocol.completion.callback;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.completion.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.simulation.FunctionCallContentBuffer;
import com.ke.bella.openapi.simulation.FunctionCallListener;
import com.ke.bella.openapi.simulation.SimulationHepler;
import org.apache.commons.lang3.StringUtils;

public class ToolCallSimulatorCallback extends Callbacks.StreamCompletionCallbackNode {
    private final FunctionCallContentBuffer buffer;
    private final CompletionProperty property;
    public ToolCallSimulatorCallback(CompletionProperty property) {
        this.property = property;
        this.buffer = new FunctionCallContentBuffer();
        if(property.isFunctionCallSimulate()) {
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
                    next.finish(ChannelException.fromException(e));
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    public void callback(StreamCompletionResponse msg) {
        if(!property.isFunctionCallSimulate()) {
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
        buffer.finish();
    }
}
