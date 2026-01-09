package com.ke.bella.openapi.script;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ScriptType {
    metrics("metrics"),
    metricsQuery("metrics/query"),
    limiter("limiter"),
    ;

    final String path;

    public String getScriptName(String fileName) {
        return this.path + fileName;
    }
}
