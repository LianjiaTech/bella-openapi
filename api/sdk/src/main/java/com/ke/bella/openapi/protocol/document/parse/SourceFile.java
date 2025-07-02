package com.ke.bella.openapi.protocol.document.parse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * 文档来源信息
 */
@Data
public class SourceFile {
    /**
     * 文件ID
     */
    @NotEmpty
    private String id;

    /**
     * 文件名
     */
    @NotEmpty
    private String name;

    /**
     * 文件类型，例如：pdf
     */
    private String type;

    /**
     * MIME类型，例如：application/pdf
     */
    @JsonProperty("mime_type")
    private String mimeType;
}
