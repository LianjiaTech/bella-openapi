package com.ke.bella.openapi.protocol.ocr.bankcard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BaiduBankcardRequest {
    String image;
    String url;
    @Builder.Default
    String location = "false";
    @Builder.Default
    String detectQuality = "false";
}
