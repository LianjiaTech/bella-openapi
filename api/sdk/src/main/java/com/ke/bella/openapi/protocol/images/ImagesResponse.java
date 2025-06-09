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
     * The Unix timestamp (in seconds) of when the image was created.
     */
    private Long created;
    
    /**
     * The list of generated images.
     */
    private List<ImageData> data;
    
    @Data
    @JsonInclude(Include.NON_NULL)
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageData {
        /**
         * The base64-encoded JSON of the generated image, if response_format is b64_json.
         */
        private String b64_json;
        
        /**
         * The URL of the generated image, if response_format is url (default).
         */
        private String url;
        
        /**
         * The prompt that was used to generate the image, if there was any revision to the prompt.
         */
        private String revised_prompt;
        
        /**
         * The width of the generated image.
         */
        private Integer width;
        
        /**
         * The height of the generated image.
         */
        private Integer height;
    }
}
