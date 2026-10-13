package com.ke.bella.openapi.protocol.images;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.ke.bella.openapi.ISummary;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片编辑请求参数
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class ImagesEditRequest implements UserRequest, ISummary, Serializable, IMemoryClearable {
    private static final long serialVersionUID = 1L;

    /**
     * 要编辑的图片文件。具体格式、尺寸及数量限制由上游模型决定。
     */
    @Nullable
    @JsonIgnore
    private MultipartFile[] image;

    /**
     * 一个额外的图片，用于指示应该编辑的区域。具体限制由上游模型决定。
     */
    @Nullable
    @JsonIgnore
    private MultipartFile mask;

    /**
     * 要编辑的图片的URL地址，具体格式及限制由上游模型决定。
     */
    @Nullable
    private String[] image_url;

    /**
     * 要编辑的图片的Base64编码数据，具体格式及限制由上游模型决定。
     */
    @Nullable
    private String[] image_b64_json;

    /**
     * 描述所需图片的文本，具体长度限制由上游模型决定。
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
     * 生成图片的尺寸，具体取值由上游模型决定。
     */
    @Nullable
    private String size;

    @Nullable
    private String quality;

    @Nullable
    private String background;

    @Nullable
    private String input_fidelity;

    @Nullable
    private String moderation;

    @Nullable
    private Integer output_compression;

    @Nullable
    private String output_format;

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
    @JsonIgnore
    private Map<String, Object> extra_body;

    /**
     * Specific extra body field, serialized as extra_body
     */
    @JsonIgnore
    private Object realExtraBody;

    /**
     * Flatten extra_body fields to the outer JSON during serialization
     */
    @JsonAnyGetter
    public Map<String, Object> getExtraBodyFields() {
        Map<String, Object> result = new HashMap<>();

        // Add regular extra_body fields
        if(extra_body != null) {
            result.putAll(extra_body);
        }

        // Add realExtraBody as extra_body field
        if(realExtraBody != null) {
            result.put("extra_body", realExtraBody);
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Handle unknown properties during deserialization and store them in
     * extra_body
     */
    public void setExtraBodyField(String key, Object value) {
        if(extra_body == null) {
            extra_body = new HashMap<>();
        }
        extra_body.put(key, value);
    }

    @Override
    public String[] ignoreFields() {
        return new String[] { "image", "image_b64_json", "mask" };
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            // 清理最大的内存占用 - 图片相关数据
            this.image = null;
            this.mask = null;
            this.image_b64_json = null;
            this.extra_body = null;
            this.realExtraBody = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
