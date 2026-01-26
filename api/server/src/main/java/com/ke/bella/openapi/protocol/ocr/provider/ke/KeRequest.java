package com.ke.bella.openapi.protocol.ocr.provider.ke;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeRequest implements IMemoryClearable, ITransfer {

    @Builder.Default
    private String requestId = null;
    @Builder.Default
    private String businessId = null;

    private RequestData data;

    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public boolean isCleared() {
        return cleared;
    }
    @Override
    public void clearLargeData(){
        if(!cleared){
            this.data = null;
            this.cleared = true;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestData {
        private String imageUrl;
        private String imageBase64;
        @Builder.Default
        private List<String> keyNameList = null;
    }

}
