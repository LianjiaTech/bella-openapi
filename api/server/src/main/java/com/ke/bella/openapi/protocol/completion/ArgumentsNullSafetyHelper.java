package com.ke.bella.openapi.protocol.completion;

import org.apache.commons.collections4.CollectionUtils;
import java.util.List;

/**
 * 用于确保arguments字段不为null的工具类
 */
public class ArgumentsNullSafetyHelper {
    
    /**
     * 确保Message中的所有ToolCall的arguments不为null
     */
    public static void ensureArgumentsNotNull(Message message) {
        if (message != null && CollectionUtils.isNotEmpty(message.getTool_calls())) {
            message.getTool_calls().forEach(ArgumentsNullSafetyHelper::ensureArgumentsNotNull);
        }
    }
    
    /**
     * 确保ToolCall的arguments不为null
     */
    public static void ensureArgumentsNotNull(Message.ToolCall toolCall) {
        if (toolCall != null && toolCall.getFunction() != null) {
            ensureArgumentsNotNull(toolCall.getFunction());
        }
    }
    
    /**
     * 确保FunctionCall的arguments不为null
     */
    public static void ensureArgumentsNotNull(Message.FunctionCall functionCall) {
        if (functionCall != null && functionCall.getArguments() == null) {
            functionCall.setArguments("");
        }
    }
    
    /**
     * 确保StreamCompletionResponse中的arguments不为null
     */
    public static void ensureArgumentsNotNull(StreamCompletionResponse response) {
        if (response != null && CollectionUtils.isNotEmpty(response.getChoices())) {
            response.getChoices().forEach(choice -> {
                if (choice.getDelta() != null) {
                    ensureArgumentsNotNull(choice.getDelta());
                }
            });
        }
    }
}
