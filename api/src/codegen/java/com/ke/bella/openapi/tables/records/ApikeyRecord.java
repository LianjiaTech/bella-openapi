/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables.records;


import com.ke.bella.openapi.db.repo.Operator;
import com.ke.bella.openapi.tables.Apikey;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * ak
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ApikeyRecord extends UpdatableRecordImpl<ApikeyRecord> implements Operator {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>apikey.id</code>. 主键ID
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>apikey.id</code>. 主键ID
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>apikey.code</code>. ak编码
     */
    public void setCode(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>apikey.code</code>. ak编码
     */
    public String getCode() {
        return (String) get(1);
    }

    /**
     * Setter for <code>apikey.ak_sha</code>. 加密ak
     */
    public void setAkSha(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>apikey.ak_sha</code>. 加密ak
     */
    public String getAkSha() {
        return (String) get(2);
    }

    /**
     * Setter for <code>apikey.ak_display</code>. 脱敏ak
     */
    public void setAkDisplay(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>apikey.ak_display</code>. 脱敏ak
     */
    public String getAkDisplay() {
        return (String) get(3);
    }

    /**
     * Setter for <code>apikey.name</code>. 名字
     */
    public void setName(String value) {
        set(4, value);
    }

    /**
     * Getter for <code>apikey.name</code>. 名字
     */
    public String getName() {
        return (String) get(4);
    }

    /**
     * Setter for <code>apikey.parent_code</code>. 父ak
     */
    public void setParentCode(String value) {
        set(5, value);
    }

    /**
     * Getter for <code>apikey.parent_code</code>. 父ak
     */
    public String getParentCode() {
        return (String) get(5);
    }

    /**
     * Setter for <code>apikey.out_entity_code</code>. 授权实体code
     */
    public void setOutEntityCode(String value) {
        set(6, value);
    }

    /**
     * Getter for <code>apikey.out_entity_code</code>. 授权实体code
     */
    public String getOutEntityCode() {
        return (String) get(6);
    }

    /**
     * Setter for <code>apikey.service_id</code>. 服务id
     */
    public void setServiceId(String value) {
        set(7, value);
    }

    /**
     * Getter for <code>apikey.service_id</code>. 服务id
     */
    public String getServiceId() {
        return (String) get(7);
    }

    /**
     * Setter for <code>apikey.owner_type</code>. 所有者类型（系统/组织/个人）
     */
    public void setOwnerType(String value) {
        set(8, value);
    }

    /**
     * Getter for <code>apikey.owner_type</code>. 所有者类型（系统/组织/个人）
     */
    public String getOwnerType() {
        return (String) get(8);
    }

    /**
     * Setter for <code>apikey.owner_code</code>. 所有者系统号
     */
    public void setOwnerCode(String value) {
        set(9, value);
    }

    /**
     * Getter for <code>apikey.owner_code</code>. 所有者系统号
     */
    public String getOwnerCode() {
        return (String) get(9);
    }

    /**
     * Setter for <code>apikey.owner_name</code>. 所有者名称
     */
    public void setOwnerName(String value) {
        set(10, value);
    }

    /**
     * Getter for <code>apikey.owner_name</code>. 所有者名称
     */
    public String getOwnerName() {
        return (String) get(10);
    }

    /**
     * Setter for <code>apikey.role_code</code>. 角色编码
     */
    public void setRoleCode(String value) {
        set(11, value);
    }

    /**
     * Getter for <code>apikey.role_code</code>. 角色编码
     */
    public String getRoleCode() {
        return (String) get(11);
    }

    /**
     * Setter for <code>apikey.certify_code</code>. 安全认证码
     */
    public void setCertifyCode(String value) {
        set(12, value);
    }

    /**
     * Getter for <code>apikey.certify_code</code>. 安全认证码
     */
    public String getCertifyCode() {
        return (String) get(12);
    }

    /**
     * Setter for <code>apikey.safety_level</code>. 安全等级
     */
    public void setSafetyLevel(Byte value) {
        set(13, value);
    }

    /**
     * Getter for <code>apikey.safety_level</code>. 安全等级
     */
    public Byte getSafetyLevel() {
        return (Byte) get(13);
    }

    /**
     * Setter for <code>apikey.month_quota</code>. 每月额度
     */
    public void setMonthQuota(BigDecimal value) {
        set(14, value);
    }

    /**
     * Getter for <code>apikey.month_quota</code>. 每月额度
     */
    public BigDecimal getMonthQuota() {
        return (BigDecimal) get(14);
    }

    /**
     * Setter for <code>apikey.status</code>. 状态(active/inactive)
     */
    public void setStatus(String value) {
        set(15, value);
    }

    /**
     * Getter for <code>apikey.status</code>. 状态(active/inactive)
     */
    public String getStatus() {
        return (String) get(15);
    }

    /**
     * Setter for <code>apikey.remark</code>. 备注
     */
    public void setRemark(String value) {
        set(16, value);
    }

    /**
     * Getter for <code>apikey.remark</code>. 备注
     */
    public String getRemark() {
        return (String) get(16);
    }

    /**
     * Setter for <code>apikey.cuid</code>. 创建人id
     */
    public void setCuid(Long value) {
        set(17, value);
    }

    /**
     * Getter for <code>apikey.cuid</code>. 创建人id
     */
    public Long getCuid() {
        return (Long) get(17);
    }

    /**
     * Setter for <code>apikey.cu_name</code>. 创建人姓名
     */
    public void setCuName(String value) {
        set(18, value);
    }

    /**
     * Getter for <code>apikey.cu_name</code>. 创建人姓名
     */
    public String getCuName() {
        return (String) get(18);
    }

    /**
     * Setter for <code>apikey.muid</code>. 编辑人id
     */
    public void setMuid(Long value) {
        set(19, value);
    }

    /**
     * Getter for <code>apikey.muid</code>. 编辑人id
     */
    public Long getMuid() {
        return (Long) get(19);
    }

    /**
     * Setter for <code>apikey.mu_name</code>. 编辑人姓名
     */
    public void setMuName(String value) {
        set(20, value);
    }

    /**
     * Getter for <code>apikey.mu_name</code>. 编辑人姓名
     */
    public String getMuName() {
        return (String) get(20);
    }

    /**
     * Setter for <code>apikey.ctime</code>.
     */
    public void setCtime(LocalDateTime value) {
        set(21, value);
    }

    /**
     * Getter for <code>apikey.ctime</code>.
     */
    public LocalDateTime getCtime() {
        return (LocalDateTime) get(21);
    }

    /**
     * Setter for <code>apikey.mtime</code>.
     */
    public void setMtime(LocalDateTime value) {
        set(22, value);
    }

    /**
     * Getter for <code>apikey.mtime</code>.
     */
    public LocalDateTime getMtime() {
        return (LocalDateTime) get(22);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached ApikeyRecord
     */
    public ApikeyRecord() {
        super(Apikey.APIKEY);
    }

    /**
     * Create a detached, initialised ApikeyRecord
     */
    public ApikeyRecord(Long id, String code, String akSha, String akDisplay, String name, String parentCode, String outEntityCode, String serviceId, String ownerType, String ownerCode, String ownerName, String roleCode, String certifyCode, Byte safetyLevel, BigDecimal monthQuota, String status, String remark, Long cuid, String cuName, Long muid, String muName, LocalDateTime ctime, LocalDateTime mtime) {
        super(Apikey.APIKEY);

        setId(id);
        setCode(code);
        setAkSha(akSha);
        setAkDisplay(akDisplay);
        setName(name);
        setParentCode(parentCode);
        setOutEntityCode(outEntityCode);
        setServiceId(serviceId);
        setOwnerType(ownerType);
        setOwnerCode(ownerCode);
        setOwnerName(ownerName);
        setRoleCode(roleCode);
        setCertifyCode(certifyCode);
        setSafetyLevel(safetyLevel);
        setMonthQuota(monthQuota);
        setStatus(status);
        setRemark(remark);
        setCuid(cuid);
        setCuName(cuName);
        setMuid(muid);
        setMuName(muName);
        setCtime(ctime);
        setMtime(mtime);
    }
}
