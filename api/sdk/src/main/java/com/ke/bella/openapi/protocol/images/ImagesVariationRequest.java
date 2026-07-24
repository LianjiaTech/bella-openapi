package com.ke.bella.openapi.protocol.images;

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
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 图片变化请求参数
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class ImagesVariationRequest implements UserRequest, ISummary, Serializable, IMemoryClearable {
    private static final long serialVersionUID = 1L;

    /**
     * 用于生成变化的图片文件。必须是有效的PNG文件，小于4MB，且为正方形。
     */
    @NotNull
    @JsonIgnore
    private MultipartFile image;

    /**
     * 用于图片变化的模型。
     */
    @Nullable
    private String model;

    /**
     * 要生成的图片数量。必须在1到10之间。对于dall-e-3，只支持n=1。
     */
    @Nullable
    private Integer n;

    /**
     * 返回生成图片的格式。必须是url或b64_json之一。
     */
    @Nullable
    private String response_format;

    /**
     * 生成图片的尺寸。必须是256x256、512x512或1024x1024之一。
     */
    @Nullable
    private String size;

    /**
     * 代表最终用户的唯一标识符，可以帮助OpenAI监控和检测滥用。
     */
    @Nullable
    private String user;

    @Override
    public String[] ignoreFields() {
        return new String[] { "image" };
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            // 清理最大的内存占用 - 图片文件
            this.image = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
