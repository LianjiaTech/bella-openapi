package com.ke.bella.openapi.protocol.ocr;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.ISummary;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.UserRequest;
import com.ke.bella.openapi.protocol.ocr.validation.ExactlyOneOf;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@ExactlyOneOf
public class OcrRequest implements UserRequest, ISummary, Serializable, IMemoryClearable {
    private static final long serialVersionUID = 1L;

    private String user;                    // 用户标识

    @NotBlank(message = "model参数不能为空")
    private String model;                   // 模型名称，必选

    // 三选一：图片输入方式
    @JsonProperty("image_base64")
    private String imageBase64;            // Base64编码的图片
    @JsonProperty("image_url")
    private String imageUrl;               // 图片URL
    @JsonProperty("file_id")
    private String fileId;                 // 文件服务中的文件ID

    /**
     * 扩展参数 - 平铺到 JSON 根级别
     */
    @JsonIgnore
    private Map<String, Object> extra_body;

    /**
     * 嵌套的 extra_body 字段
     */
    @JsonIgnore
    private Object realExtraBody;

    /**
     * 将 extra_body 字段平铺到 JSON，并处理 realExtraBody
     */
    @JsonAnyGetter
    public Map<String, Object> getExtraBodyFields() {
        Map<String, Object> result = new HashMap<>();

        // 平铺 extra_body
        if (extra_body != null) {
            result.putAll(extra_body);
        }

        // 嵌套 realExtraBody
        if (realExtraBody != null) {
            result.put("extra_body", realExtraBody);
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * 捕获未知字段到 extra_body
     */
    @JsonAnySetter
    public void setExtraBodyField(String key, Object value) {
        if (extra_body == null) {
            extra_body = new HashMap<>();
        }
        extra_body.put(key, value);
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public String[] ignoreFields() {
        return new String[] { "imageBase64" };
    }

    @Override
    public void clearLargeData() {
        if(!cleared) {
            this.imageBase64 = null;
            this.cleared = true;
        }
    }
}
