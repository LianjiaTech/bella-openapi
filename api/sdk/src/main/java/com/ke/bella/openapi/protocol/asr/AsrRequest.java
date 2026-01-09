package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.ISummary;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AsrRequest implements ISummary, IMemoryClearable {
    @JsonIgnore
    byte[] content;
    String model;
    String format;
    Integer maxSentenceSilence;
    Integer sampleRate;
    String hotWords;
    String hotWordsTableId;
    Boolean convertNumbers;

    @Override
    public String[] ignoreFields() {
        return new String[] { "content" };
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            // 清理最大的内存占用 - 音频内容字节数组
            this.content = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
