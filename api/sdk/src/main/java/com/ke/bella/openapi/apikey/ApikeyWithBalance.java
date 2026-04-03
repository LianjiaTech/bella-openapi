package com.ke.bella.openapi.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API Key 带余额信息的数据传输对象
 * @author Claude Code
 * @since 2026-04-02
 * @see com.ke.bella.openapi.tables.pojos.ApikeyDB
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApikeyWithBalance implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== API Key 基础信息（来自 apikey 表）====================

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * API Key 编码（唯一标识）
     */
    private String code;

    /**
     * 加密 API Key（SHA256）
     */
    private String akSha;

    /**
     * 脱敏显示的 API Key（如：bd****ae0d）
     */
    private String akDisplay;

    /**
     * API Key 名称
     */
    private String name;

    /**
     * 父 API Key 编码（用于子 Key 场景）
     */
    private String parentCode;

    /**
     * 授权实体编码
     */
    private String outEntityCode;

    /**
     * 服务 ID
     */
    private String serviceId;

    /**
     * 所有者类型（person/org/system）
     */
    private String ownerType;

    /**
     * 所有者编码（用户系统号）
     */
    private String ownerCode;

    /**
     * 所有者名称
     */
    private String ownerName;

    /**
     * 角色编码（权限级别）
     */
    private String roleCode;

    /**
     * 安全认证码
     */
    private String certifyCode;

    /**
     * 安全认证场景编码
     */
    private String safetySceneCode;

    /**
     * 安全等级
     */
    private Byte safetyLevel;

    /**
     * 每月额度（单位：元）
     */
    private BigDecimal monthQuota;

    /**
     * 状态（active/inactive）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人 ID
     */
    private Long cuid;

    /**
     * 创建人姓名
     */
    private String cuName;

    /**
     * 编辑人 ID
     */
    private Long muid;

    /**
     * 编辑人姓名
     */
    private String muName;

    /**
     * 创建时间
     */
    private LocalDateTime ctime;

    /**
     * 修改时间
     */
    private LocalDateTime mtime;

    // ==================== 余额相关信息（来自 apikey_month_cost 表）====================

    /**
     * 当前月份（格式：2026-04）
     * <p>由后端自动生成，用于标识消费数据的月份
     */
    private String currentMonth;

    /**
     * 当月消费（单位：元）
     * <p>通过 LEFT JOIN apikey_month_cost 表获取
     * <p>如果当月无消费记录，则为 0.00
     */
    private BigDecimal monthCost;

    // ==================== 计算属性 ====================

    /**
     * 获取当前余额（计算属性）
     * <p>计算公式：余额 = 月额度 - 月消费
     * @return 当前余额（单位：元，保留 2 位小数），如果额度或消费为 null，返回 BigDecimal.ZERO
     */
    public BigDecimal getBalance() {
        if (monthQuota == null) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (monthCost == null) {
            return monthQuota.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return monthQuota.subtract(monthCost).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
