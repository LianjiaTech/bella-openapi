package com.ke.bella.openapi.protocol.completion.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class GeminiRequest {
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
}
