
package com.ke.bella.openapi.simulation;

import java.util.ArrayList;
import java.util.List;

import com.ke.bella.openapi.protocol.completion.Message.ToolCall;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.Getter;

public class PythonFuncCallListener {

    private FunctionCallListener callback;
    @Getter
    private List<ToolCall> toolcalls = new ArrayList<>();
    private ToolCall currentToolCall;
    @Getter
    private StringBuilder buffer = new StringBuilder();

    public PythonFuncCallListener(FunctionCallListener callback) {
        this.callback = callback;
    }

    public void onFunctionName(String name, int index) {
        currentToolCall = ToolCall.fromFunctionName(name, index);
        if(callback != null) {
            callback.onMessage(StreamCompletionResponse.builder()
                    .choices(StreamCompletionResponse.toolcallChoice(currentToolCall))
                    .build());
        }
    }

    public void onFunctionCallEnter() {
        currentToolCall.getFunction().setArguments("{");
        if(callback != null) {
            currentToolCall.getFunction().setName(null); // 流式：只有function
                                                         // call的首包发送functionName
            callback.onMessage(StreamCompletionResponse.builder()
                    .choices(StreamCompletionResponse.toolcallChoice(currentToolCall))
                    .build());
        }
    }

    public void onArgumentName(String name) {
        if(callback != null) {
            currentToolCall.getFunction().setName(null); // 流式：只有function
                                                         // call的首包发送functionName
            currentToolCall.getFunction().setArguments(JacksonUtils.serialize(name));
            callback.onMessage(StreamCompletionResponse.builder()
                    .choices(StreamCompletionResponse.toolcallChoice(currentToolCall))
                    .build());
        } else {
            currentToolCall.getFunction().appendArguments(JacksonUtils.serialize(name));
        }
    }

    public void onArgumentValue(Object value) {
        String tmp = ":" + JacksonUtils.serialize(value);
        if(callback != null) {
            currentToolCall.getFunction().setName(null); // 流式：只有function
                                                         // call的首包发送functionName
            currentToolCall.getFunction().setArguments(tmp);
            callback.onMessage(StreamCompletionResponse.builder()
                    .choices(StreamCompletionResponse.toolcallChoice(currentToolCall))
                    .build());
        } else {
            currentToolCall.getFunction().appendArguments(tmp);
        }

    }

    public void onNextArgumentEnter() {
        if(callback != null) {
            currentToolCall.getFunction().setName(null); // 流式：只有function
                                                         // call的首包发送functionName
            currentToolCall.getFunction().setArguments(",");
            callback.onMessage(StreamCompletionResponse.builder()
                    .choices(StreamCompletionResponse.toolcallChoice(currentToolCall))
                    .build());
        } else {
            currentToolCall.getFunction().appendArguments(",");
        }
    }

    public void onFunctionCallExit() {
        if(callback != null) {
            currentToolCall.getFunction().setName(null); // 流式：只有function
                                                         // call的首包发送functionName
            currentToolCall.getFunction().setArguments("}");
            callback.onMessage(StreamCompletionResponse.builder()
                    .choices(StreamCompletionResponse.toolcallChoice(currentToolCall))
                    .build());
        } else {
            currentToolCall.getFunction().appendArguments("}");
            toolcalls.add(currentToolCall);
        }
    }

    public void onDirectlyResponseEnter() {
        // no-op

    }

    public void onDirectlyResponseExit() {
        // no-op
    }

    public void onDirectlyResponseType(String type) {

    }

    public void onDirectlyResponseContent(String delta) {
        if(callback != null) {
            callback.onMessage(StreamCompletionResponse.builder()
                    .choices(StreamCompletionResponse.assistantMessageChoice(delta))
                    .build());
        } else {
            buffer.append(delta);
        }
    }

    public void onFinish() {
        if(callback != null) {
            callback.onFinish();
        }
    }
}
