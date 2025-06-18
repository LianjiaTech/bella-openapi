package com.ke.bella.openapi.script;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ScriptType {
    metrics("metrics"),
    metricsQuery("metrics/query"),
    limiter("limiter"),
    custom(""),
    ;

    final String path;
    public String getScriptName(String fileName) {
        if (this == custom) {
            return fileName;
        }
        return this.path + "/" + fileName;
    }
}
