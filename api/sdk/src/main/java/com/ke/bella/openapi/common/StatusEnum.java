package com.ke.bella.openapi.common;

import lombok.Getter;

/**
 * function: 状态枚举
 *
 * @author chenhongliang001
 */
@Getter
public enum StatusEnum {

    VALID((byte) 0, "有效"),

    INVALID((byte) -1, "无效");

    StatusEnum(Byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private Byte code;

    private String desc;
}
