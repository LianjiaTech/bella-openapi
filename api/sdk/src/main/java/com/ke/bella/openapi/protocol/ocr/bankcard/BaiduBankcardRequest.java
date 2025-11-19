package com.ke.bella.openapi.protocol.ocr.bankcard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BaiduBankcardRequest implements IMemoryClearable, ITransfer {
    String image;
    String url;
    @Builder.Default
    String location = "false";
    @Builder.Default
    @JsonProperty("detect_quality")
    String detectQuality = "false";

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if (!cleared) {
            // 清理最大的内存占用 - 图像数据
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
