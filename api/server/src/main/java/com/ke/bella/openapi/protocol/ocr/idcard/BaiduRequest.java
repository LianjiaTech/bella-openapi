package com.ke.bella.openapi.protocol.ocr.idcard;

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
    public static final String ID_CARD_FRONT = "front";

    @Builder.Default
    private String idCardSide = ID_CARD_FRONT;
    @Builder.Default
    private String detectPs = "false";
    @Builder.Default
    private String detectRisk = "false";
    @Builder.Default
    private String detectQuality = "false";
    @Builder.Default
    private String detectPhoto = "false";
    @Builder.Default
    private String detectCard = "false";
    @Builder.Default
    private String detectDirection = "false";
    @Builder.Default
    private String detectScreenshot = "false";
}
