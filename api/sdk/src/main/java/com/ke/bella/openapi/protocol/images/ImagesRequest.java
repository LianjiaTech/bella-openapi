package com.ke.bella.openapi.protocol.images;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nullable;
import java.io.Serializable;

@Data
@SuperBuilder
@NoArgsConstructor
public class ImagesRequest implements UserRequest, Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * A text description of the desired image(s). The maximum length is 1000 characters for dall-e-2 and 4000 characters for dall-e-3.
     */
    private String prompt;
    
    /**
     * The model to use for image generation.
     */
    @Nullable
    private String model;
    
    /**
     * The number of images to generate. Must be between 1 and 10. For dall-e-3, only n=1 is supported.
     */
    @Nullable
    private Integer n = 1;
    
    /**
     * The quality of the image that will be generated. hd creates images with finer details and greater consistency across the image. This param is only supported for dall-e-3.
     */
    @Nullable
    private String quality;
    
    /**
     * The format in which the generated images are returned. Must be one of url or b64_json.
     */
    @Nullable
    private String response_format = "url";
    
    /**
     * The size of the generated images. Must be one of 256x256, 512x512, or 1024x1024 for dall-e-2. Must be one of 1024x1024, 1792x1024, or 1024x1792 for dall-e-3 models.
     */
    @Nullable
    private String size = "1024x1024";
    
    /**
     * The style of the generated images. Must be one of vivid or natural. Vivid causes the model to lean towards generating hyper-real and dramatic images. Natural causes the model to produce more natural, less hyper-real looking images. This param is only supported for dall-e-3.
     */
    @Nullable
    private String style;
    
    /**
     * A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
     */
    @Nullable
    private String user;
    
    /**
     * The width of the generated image. Only supported for some models.
     */
    @Nullable
    private Integer width;
    
    /**
     * The height of the generated image. Only supported for some models.
     */
    @Nullable
    private Integer height;
    
    /**
     * Additional parameters for specific models
     */
    @JsonInclude(Include.NON_NULL)
    @Nullable
    private Object extra_params;
}
