package com.ke.bella.openapi.safety;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SafetyCheckResult {
    // passed：通过
    // warning：不拦截，但是需要提示用户
    // failed：拦截
    private String status;
    // status是warning/failed的时候透传给用户，由安全网关定义
    private Object data;

    public enum Status {
        passed,
        warning,
        failed
    }
}
