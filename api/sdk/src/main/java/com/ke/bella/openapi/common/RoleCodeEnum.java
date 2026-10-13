package com.ke.bella.openapi.common;

import lombok.Getter;

/**
 * function:
 *
 * @author chenhongliang001
 */
@Getter
public enum RoleCodeEnum {

    OWNER("owner", "拥有者"),

    ADMIN("admin", "管理员"),

    MEMBER("member", "成员");

    RoleCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private String code;

    private String desc;
}
