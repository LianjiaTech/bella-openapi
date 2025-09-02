package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;

/**
 * Responses API SSE 事件转换器
 * 将 Responses API 的 SSE 事件转换为 Chat Completion 流式响应格式
 */
@Slf4j
public class ResponsesApiSseConverter implements Callbacks.SseEventConverter<StreamCompletionResponse> {
    
    private String responseId;
    private String model;
    private final long created;
    private boolean hasToolCalls = false;
    private int toolCallIndex = 0;

    public ResponsesApiSseConverter() {
        this.created = DateTimeUtils.getCurrentSeconds();
    }

    @Override
    public StreamCompletionResponse convert(String eventId, String eventType, String eventData) {
        try {
            ResponsesApiStreamEvent event = JacksonUtils.deserialize(eventData, ResponsesApiStreamEvent.class);
            if (event != null) {
                return processStreamEvent(event);
            }
            return null;
        } catch (Exception e) {
            log.error("Error processing Responses API stream event: {}", eventData, e);
            return null;
        }
    }

    /**
     * 处理流式事件
     */
    private StreamCompletionResponse processStreamEvent(ResponsesApiStreamEvent event) {
        switch (event.getType()) {
            case "response.created":
                return handleResponseCreated(event);
            case "response.in_progress":
                return handleResponseInProgress(event);
            case "response.output_item.added":
                return handleOutputItemAdded(event);
            case "response.output_text.delta":
                return handleOutputTextDelta(event);
            case "response.function_call_arguments.delta":
                return handleFunctionCallArgumentsDelta(event);
            case "response.function_call_arguments.done":
                return handleFunctionCallArgumentsDone(event);
            case "response.reasoning_summary_text.delta":
                return handleReasoningSummaryDelta(event);
            case "response.reasoning_summary_text.done":
                return handleReasoningSummaryDone(event);
            case "response.output_item.done":
                return handleOutputItemDone(event);
            case "response.completed":
                return handleResponseCompleted(event);
            case "response.error":
                return handleResponseError(event);
            default:
                log.debug("Unknown Responses API event type: {}", event.getType());
                return null;
        }
    }

    /**
     * 处理响应创建事件
     */
    private StreamCompletionResponse handleResponseCreated(ResponsesApiStreamEvent event) {
        // 从 response 对象中提取信息
        if (event.getResponse() != null) {
            ResponsesApiResponse response = event.getResponse();
            this.responseId = response.getId();
            this.model = response.getModel();
        }
        
        return null;
    }

    /**
     * 处理响应进行中事件
     */
    private StreamCompletionResponse handleResponseInProgress(ResponsesApiStreamEvent event) {
        // 通常不需要为 in_progress 发送特殊的 chunk
        return null;
    }

    /**
     * 处理输出项添加事件
     */
    private StreamCompletionResponse handleOutputItemAdded(ResponsesApiStreamEvent event) {
        // 从 item 对象中获取类型信息
        if (event.getItem() != null) {
            ResponsesApiResponse.OutputItem item = event.getItem();
            String itemType = item.getType();
            
            if ("function_call".equals(itemType)) {
                hasToolCalls = true;
                return sendToolCallStart(event);
            }
            // reasoning 和 message 类型在这里不需要特殊处理
        }
        return null;
    }

    /**
     * 处理输出文本增量事件
     */
    private StreamCompletionResponse handleOutputTextDelta(ResponsesApiStreamEvent event) {
        String delta = event.getDelta();
        if (StringUtils.isNotBlank(delta)) {
            return StreamCompletionResponse.builder()
                    .id(responseId)
                    .object("chat.completion.chunk")
                    .created(created)
                    .model(model)
                    .choices(Collections.singletonList(
                            StreamCompletionResponse.Choice.builder()
                                    .index(0)
                                    .delta(Message.builder().content(delta).build())
                                    .finish_reason(null)
                                    .build()
                    ))
                    .build();
        }
        return null;
    }

    /**
     * 处理函数调用参数增量事件
     */
    private StreamCompletionResponse handleFunctionCallArgumentsDelta(ResponsesApiStreamEvent event) {
        String delta = event.getDelta();
        
        if (StringUtils.isNotBlank(delta)) {
            // 发送工具调用参数增量
            return StreamCompletionResponse.builder()
                    .id(responseId)
                    .object("chat.completion.chunk")
                    .created(created)
                    .model(model)
                    .choices(Collections.singletonList(
                            StreamCompletionResponse.Choice.builder()
                                    .index(0)
                                    .delta(Message.builder()
                                            .tool_calls(Collections.singletonList(
                                                    Message.ToolCall.builder()
                                                            .index(toolCallIndex)
                                                            .function(Message.FunctionCall.builder()
                                                                    .arguments(delta)
                                                                    .build())
                                                            .build()
                                            ))
                                            .build())
                                    .finish_reason(null)
                                    .build()
                    ))
                    .build();
        }
        return null;
    }

    /**
     * 处理函数调用参数完成事件
     */
    private StreamCompletionResponse handleFunctionCallArgumentsDone(ResponsesApiStreamEvent event) {
        // 工具调用参数构建完成，不需要额外处理
        toolCallIndex++;
        log.debug("Function call arguments done for item: {}", event.getItem_id());
        return null;
    }

    /**
     * 处理推理摘要增量事件
     */
    private StreamCompletionResponse handleReasoningSummaryDelta(ResponsesApiStreamEvent event) {
        String delta = event.getDelta();
        if (StringUtils.isNotBlank(delta)) {
            // 发送推理内容增量
            return StreamCompletionResponse.builder()
                    .id(responseId)
                    .object("chat.completion.chunk")
                    .created(created)
                    .model(model)
                    .choices(Collections.singletonList(
                            StreamCompletionResponse.Choice.builder()
                                    .index(0)
                                    .delta(Message.builder().reasoning_content(delta).build())
                                    .finish_reason(null)
                                    .build()
                    ))
                    .build();
        }
        return null;
    }

    /**
     * 处理推理摘要完成事件
     */
    private StreamCompletionResponse handleReasoningSummaryDone(ResponsesApiStreamEvent event) {
        // 推理摘要构建完成
        log.debug("Reasoning summary done for item: {}", event.getItem_id());
        return null;
    }

    /**
     * 处理输出项完成事件
     */
    private StreamCompletionResponse handleOutputItemDone(ResponsesApiStreamEvent event) {
        // 输出项完成，可能需要根据类型做特殊处理
        log.debug("Output item done for index: {}", event.getOutput_index());
        return null;
    }

    /**
     * 处理响应完成事件
     */
    private StreamCompletionResponse handleResponseCompleted(ResponsesApiStreamEvent event) {
        String finishReason = hasToolCalls ? "tool_calls" : "stop";
        
        return StreamCompletionResponse.builder()
                .id(responseId)
                .object("chat.completion.chunk")
                .created(created)
                .model(model)
                .choices(Collections.singletonList(
                        StreamCompletionResponse.Choice.builder()
                                .index(0)
                                .delta(Message.builder().build())
                                .finish_reason(finishReason)
                                .build()
                ))
                .usage(ResponsesApiConverter.convertToken(event.getResponse().getUsage()))
                .build();
    }

    /**
     * 处理响应错误事件
     */
    private StreamCompletionResponse handleResponseError(ResponsesApiStreamEvent event) {
        log.error("Responses API error: {}", event.getResponse().getError());
        // 返回包含错误信息的响应
        return StreamCompletionResponse.builder()
                .id(responseId)
                .object("chat.completion.chunk")
                .created(created)
                .model(model)
                .error(event.getResponse().getError())
                .build();
    }

    /**
     * 发送工具调用开始事件
     */
    private StreamCompletionResponse sendToolCallStart(ResponsesApiStreamEvent event) {
        String callId = null;
        String functionName = null;
        
        // 从 item 对象中提取工具调用信息
        if (event.getItem() != null) {
            ResponsesApiResponse.OutputItem item = event.getItem();
            callId = item.getCall_id();
            functionName = item.getName();
        }
        
        return StreamCompletionResponse.builder()
                .id(responseId)
                .object("chat.completion.chunk")
                .created(created)
                .model(model)
                .choices(Collections.singletonList(
                        StreamCompletionResponse.Choice.builder()
                                .index(0)
                                .delta(Message.builder()
                                        .tool_calls(Collections.singletonList(
                                                Message.ToolCall.builder()
                                                        .index(toolCallIndex)
                                                        .id(callId)
                                                        .type("function")
                                                        .function(Message.FunctionCall.builder()
                                                                .name(functionName)
                                                                .arguments("")
                                                                .build())
                                                        .build()
                                        ))
                                        .build())
                                .finish_reason(null)
                                .build()
                ))
                .build();
    }
}
