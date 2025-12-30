package com.ke.bella.openapi.protocol.ocr.provider.baidu;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaiduBaseResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("log_id")
    protected Long logId;
    @JsonProperty("error_code")
    protected String errorCode;
    @JsonProperty("error_msg")
    protected String errorMsg;

}
