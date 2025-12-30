package com.ke.bella.openapi.protocol.ocr.provider.baidu;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public abstract class BaiduBaseRequest implements IMemoryClearable, ITransfer {
    protected String image;
    protected String url;

    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            this.image = null;
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
