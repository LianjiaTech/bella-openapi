package com.ke.bella.openapi.protocol.ocr.idcard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BaiduOcrIdcardRequest {
    public static final String ID_CARD_FRONT = "front";

    private String image;
    private String url;
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
