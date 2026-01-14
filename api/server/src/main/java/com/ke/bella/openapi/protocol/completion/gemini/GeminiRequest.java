package com.ke.bella.openapi.protocol.completion.gemini;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.ITransfer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiRequest implements IMemoryClearable, ITransfer {
    private List<Content> contents;
    private SystemInstruction systemInstruction;
    private List<Tool> tools;
    private List<SafetySetting> safetySettings;
    private GenerationConfig generationConfig;

    // 关闭所有安全审核策略
    public GeminiRequest offSafetySettings() {
        this.safetySettings = new ArrayList<>();
        safetySettings.add(SafetySetting.builder()
                .category("HARM_CATEGORY_SEXUALLY_EXPLICIT")
                .threshold("OFF")
                .build());
        safetySettings.add(SafetySetting.builder()
                .category("HARM_CATEGORY_HATE_SPEECH")
                .threshold("OFF")
                .build());
        safetySettings.add(SafetySetting.builder()
                .category("HARM_CATEGORY_HARASSMENT")
                .threshold("OFF")
                .build());
        safetySettings.add(SafetySetting.builder()
                .category("HARM_CATEGORY_DANGEROUS_CONTENT")
                .threshold("OFF")
                .build());
        return this;
    }

    // 内存清理相关字段和方法
    @JsonIgnore
    private volatile boolean cleared = false;

    @Override
    public void clearLargeData() {
        if(!cleared) {
            // 清理最大的内存占用 - 内容列表、工具列表、系统指令等
            // 注意：某些 List 可能是不可修改的（如 Collections.singletonList），直接置 null
            this.contents = null;
            this.tools = null;
            this.safetySettings = null;
            this.systemInstruction = null;
            this.generationConfig = null;

            // 标记为已清理
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
