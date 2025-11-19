package com.ke.bella.openapi.protocol.ocr.general;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class BaiduRequest implements IMemoryClearable, ITransfer {
    String image;
    String url;
    String pdfFile;
    String pdfFileNum;
    String ofdFile;
    String ofdFileNum;
    String languageType;
    String detectDirection;
    String detectLanguage;
    @Builder.Default
    String paragraph = "false";
    @Builder.Default
    String probability = "false";

    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            this.image = null;
            this.pdfFile = null;
            this.ofdFile = null;
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
