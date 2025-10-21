package com.ke.bella.openapi.protocol.ocr.tmp_idcard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class YidaoOcrTmpIdcardRequest implements IMemoryClearable, ITransfer {

    @JsonProperty("app_key")
    private String appKey;

    @JsonProperty("app_secret")
    private String appSecret;

    @JsonProperty("image_base64")
    private String imageBase64;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            this.imageBase64 = null;
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
