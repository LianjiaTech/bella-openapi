package com.ke.bella.openapi.protocol.asr.transcription;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * OpenAI-compatible transcriptions response
 * Based on OpenAI API specification: https://platform.openai.com/docs/api-reference/audio/createTranscription
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TranscriptionsResponse extends OpenapiResponse {

    /**
     * The transcribed text.
     */
    private String text;

    /**
     * The language of the input audio.
     */
    private String language;

    /**
     * The duration of the input audio.
     */
    private Double duration;

    /**
     * Extracted words and their timestamps.
     */
    private List<Word> words;

    /**
     * Segments of the transcription and their timestamps.
     */
    private List<Segment> segments;

    /**
     * Usage information for the request.
     */
    private Usage usage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Word {
        /**
         * The text content of the word.
         */
        private String word;

        /**
         * Start time of the word in seconds.
         */
        private Double start;

        /**
         * End time of the word in seconds.
         */
        private Double end;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Segment {
        /**
         * Unique identifier of the segment.
         */
        private Integer id;

        /**
         * Seek offset of the segment.
         */
        private Integer seek;

        /**
         * Start time of the segment in seconds.
         */
        private Double start;

        /**
         * End time of the segment in seconds.
         */
        private Double end;

        /**
         * Text content of the segment.
         */
        private String text;

        /**
         * Array of token IDs for the text content.
         */
        private List<Integer> tokens;

        /**
         * Temperature parameter used for generating the segment.
         */
        private Double temperature;

        /**
         * Average logprob of the segment.
         */
        @JsonProperty("avg_logprob")
        private Double avgLogprob;

        /**
         * Compression ratio of the segment.
         */
        @JsonProperty("compression_ratio")
        private Double compressionRatio;

        /**
         * Probability of no speech in the segment.
         */
        @JsonProperty("no_speech_prob")
        private Double noSpeechProb;

        /**
         * Array of words in the segment.
         */
        private List<Word> words;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        /**
         * Usage type.
         */
        private String type;

        /**
         * Number of input tokens.
         */
        @JsonProperty("input_tokens")
        private Integer inputTokens;

        /**
         * Input token details.
         */
        @JsonProperty("input_token_details")
        private InputTokenDetails inputTokenDetails;

        /**
         * Number of output tokens.
         */
        @JsonProperty("output_tokens")
        private Integer outputTokens;

        /**
         * Total number of tokens.
         */
        @JsonProperty("total_tokens")
        private Integer totalTokens;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class InputTokenDetails {
            /**
             * Number of text tokens.
             */
            @JsonProperty("text_tokens")
            private Integer textTokens;

            /**
             * Number of audio tokens.
             */
            @JsonProperty("audio_tokens")
            private Integer audioTokens;
        }
    }
}