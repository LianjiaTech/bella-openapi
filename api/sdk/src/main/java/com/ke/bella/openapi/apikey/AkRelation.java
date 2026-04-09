package com.ke.bella.openapi.apikey;

public enum AkRelation {
    /** 调用方是目标 AK 的所有者（ownerCode 相同） */
    OWNER,
    /**
     * 调用方被授权为目标 AK 的 manager。
     * 当前版本预留，resolveRelation 中暂不实际解析，始终跳过。
     * 未来只需实现 isManager() 并取消注释即可生效。
     */
    MANAGER,
    /**
     * 调用方与目标 AK 同属一个 org。
     * 沿用现有 TODO 逻辑（orgCodes 始终为空），当前不会命中此分支。
     */
    SAME_ORG,
    /** 调用方与目标 AK 无关联 */
    UNRELATED
}
