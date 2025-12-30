package com.ke.bella.openapi.protocol.ocr.general;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
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
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class BaiduRequest extends BaiduBaseRequest {

    private String pdfFile;
    private String pdfFileNum;
    private String ofdFile;
    private String ofdFileNum;
    private String languageType;
    private String detectDirection;
    private String detectLanguage;

    @Builder.Default
    private String paragraph = "false";

    @Builder.Default
    private String probability = "false";
}
