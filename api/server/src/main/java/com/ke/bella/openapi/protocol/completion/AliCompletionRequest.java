package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

import java.util.List;


@Data
@SuperBuilder
public class AliCompletionRequest implements IMemoryClearable, ITransfer {
    @NonNull
    private String model;
    private AliCompletionInput input;
    private AliCompletionParameters parameters;

    public static class ResultFormat {
        public static String TEXT = "text";
        public static String MESSAGE = "message";
    }

    @Data
    @Builder
    public static class AliCompletionInput {
        private String prompt;
        private List<Message> messages;
    }

    @Data
    @Builder
    public static class AliCompletionParameters {
        /* @deprecated use maxTokens instead */
        @Deprecated
        private Integer maxLength;
        /* A sampling strategy, called nucleus
        sampling, where the model considers the results of the
        tokens with top_p probability mass. So 0.1 means only
        the tokens comprising the top 10% probability mass are
        considered */
        private Float topP;

        /* A sampling strategy, the k largest elements of the
        given mass are  considered */
        private Integer topK;
        /* Whether to enable web search(quark).
        Currently works best only on the first round of conversation.
        Default to False */
        @Builder.Default
        private Boolean enableSearch = false;
        /*
         * When generating, the seed of the random number is used to control the randomness of the model generation.
         * If you use the same seed, each run will generate the same results;
         * you can use the same seed when you need to reproduce the model's generated results.
         * The seed parameter supports unsigned 64-bit integer types. Default value 1234
         */
        private Integer seed;

        /** The output format, text or message, default message. */
        @Builder.Default
        private String result_format = ResultFormat.MESSAGE;

        /**
         * Used to control the degree of randomness and diversity. Specifically, the temperature value
         * controls the degree to which the probability distribution of each candidate word is smoothed
         * when generating text. A higher temperature value will reduce the peak value of the probability
         * distribution, allowing more low-probability words to be selected, and the generated results
         * will be more diverse; while a lower temperature value will enhance the peak value of the
         * probability distribution, making it easier for high-probability words to be selected, the
         * generated results are more deterministic, range(0, 2).
         */
        private Float temperature;

        /**
         * Used to control the streaming output mode. If true, the subsequent output will include the
         * previously input content by default. Otherwise, the subsequent output will not include the
         * previously output content. Default: false eg(false):
         *
         * <pre>
         * I
         * I like
         * I like apple
         * when true:
         * I
         * like
         * apple
         * </pre>
         */
        @Builder.Default
        private Boolean incremental_output = false;

        /*
         * Maximum tokens to generate.
         */
        private Integer maxTokens;
        /*
         * repetition penalty
         */
        private Float repetitionPenalty;

        /*
         * stopString and token are mutually exclusive.
         */
        private Object stopString;

        @Singular
        private List<List<Integer>> stopTokens;

        /*
         * Specify which tools the model can use.
         */
        private List<Message.Tool> tools;
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if (!cleared && input != null) {
            // 清理输入中的大型数据
            input.setMessages(null);
            input.setPrompt(null);

            // 清理参数中的工具定义
            if (parameters != null) {
                parameters.setTools(null);
            }

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
