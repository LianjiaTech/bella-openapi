package com.ke.bella.openapi.protocol.images;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@JsonInclude(Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ImagesResponse extends OpenapiResponse {

    /**
     * The background parameter used for the image generation. Either
     * transparent or opaque.
     */
    private String background;

    /**
     * The Unix timestamp (in seconds) of when the image was created.
     */
    private Long created;

    /**
     * The list of generated images.
     */
    private List<ImageData> data;

    /**
     * For gpt-image-1 only, the token usage information for the image
     * generation.
     */
    private Usage usage;

    @Data
    @JsonInclude(Include.NON_NULL)
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageData {
        /**
         * The base64-encoded JSON of the generated image, if response_format is
         * b64_json.
         */
        private String b64_json;

        /**
         * The URL of the generated image, if response_format is url (default).
         */
        private String url;

        /**
         * The prompt that was used to generate the image, if there was any
         * revision to the prompt.
         */
        private String revised_prompt;

        /**
         * The output format of the image generation. Either png, webp, or jpeg.
         */
        private String output_format;

        /**
         * The quality of the image generated. Either low, medium, or high.
         */
        private String quality;

        /**
         * The size of the image generated. Either 1024x1024, 1024x1536, or
         * 1536x1024.
         */
        private String size;
    }

    @Data
    @JsonInclude(Include.NON_NULL)
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer num;
        private String size;
        private String quality;
        /**
         * The number of tokens (images and text) in the input prompt.
         */
        private Integer input_tokens;

        /**
         * The input tokens detailed information for the image generation.
         */
        private InputTokensDetails input_tokens_details;

        /**
         * The number of image tokens in the output image.
         */
        private Integer output_tokens;

        /**
         * The total number of tokens (images and text) used for the image
         * generation.
         */
        private Integer total_tokens;
    }

    @Data
    @JsonInclude(Include.NON_NULL)
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputTokensDetails {
        /**
         * The number of image tokens in the input prompt.
         */
        private Integer image_tokens;

        /**
         * The number of text tokens in the input prompt.
         */
        private Integer text_tokens;
    }
}
