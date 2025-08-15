package com.ke.bella.openapi.protocol.images;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 图片编辑请求参数
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class ImagesEditRequest implements UserRequest, Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 要编辑的图片文件。必须是有效的PNG文件，小于4MB，且为正方形。
     * 如果未提供mask，图片必须具有透明度，该透明度将用作mask。
     */
    @Nullable
    @JsonIgnore
    private MultipartFile imageF;
    
    /**
     * 一个额外的图片，其完全透明的区域（例如alpha为零的地方）指示应该编辑图片的位置。
     * 必须是有效的PNG文件，小于4MB，且与image具有相同的尺寸。
     */
    @Nullable
    @JsonIgnore
    private MultipartFile mask;
    
    /**
     * 要编辑的图片的URL地址。必须是有效的PNG文件，小于4MB，且为正方形。
     * 如果未提供mask，图片必须具有透明度，该透明度将用作mask。
     */
    @Nullable
    private String image_url;
    
    /**
     * 要编辑的图片的base64编码JSON格式。必须是有效的PNG文件，小于4MB，且为正方形。
     * 如果未提供mask，图片必须具有透明度，该透明度将用作mask。
     */
    @Nullable
    private String image_b64_json;
    
    /**
     * 描述所需图片的文本。最大长度为1000个字符。
     */
    @NotBlank
    private String prompt;
    
    /**
     * 用于图片编辑的模型。
     */
    @Nullable
    private String model;
    
    /**
     * 要生成的图片数量。必须在1到10之间。
     */
    @Nullable
    private Integer n;
    
    /**
     * 生成图片的尺寸。必须是256x256、512x512或1024x1024之一。
     */
    @Nullable
    private String size;
    
    /**
     * 返回生成图片的格式。必须是url或b64_json之一。
     */
    @Nullable
    private String response_format;
    
    /**
     * 代表最终用户的唯一标识符，可以帮助OpenAI监控和检测滥用。
     */
    @Nullable
    private String user;
    
    @Nullable
    private String image;
}
