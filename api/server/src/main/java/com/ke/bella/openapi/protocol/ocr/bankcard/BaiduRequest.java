package com.ke.bella.openapi.protocol.ocr.bankcard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.ocr.provider.baidu.BaiduBaseRequest;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BaiduRequest extends BaiduBaseRequest {

    @Builder.Default
    private String location = "false";

    @Builder.Default
    @JsonProperty("detect_quality")
    private String detectQuality = "false";
}
