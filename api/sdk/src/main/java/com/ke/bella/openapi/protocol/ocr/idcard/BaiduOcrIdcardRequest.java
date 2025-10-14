package com.ke.bella.openapi.protocol.ocr.idcard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BaiduOcrIdcardRequest implements IMemoryClearable, ITransfer {
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

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if (!cleared) {
            // 清理最大的内存占用 - 图像数据和URL
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
