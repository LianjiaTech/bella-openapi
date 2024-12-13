/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables.records;


import com.ke.bella.openapi.db.repo.Timed;
import com.ke.bella.openapi.tables.SpaceRole;

import java.time.LocalDateTime;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record11;
import org.jooq.Row11;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * 空间角色
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class SpaceRoleRecord extends UpdatableRecordImpl<SpaceRoleRecord> implements Timed, Record11<Long, String, String, String, String, Byte, Byte, LocalDateTime, LocalDateTime, Long, Long> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>space_role.id</code>. 主键
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>space_role.id</code>. 主键
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>space_role.space_code</code>. 团队编码
     */
    public void setSpaceCode(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>space_role.space_code</code>. 团队编码
     */
    public String getSpaceCode() {
        return (String) get(1);
    }

    /**
     * Setter for <code>space_role.role_code</code>. 角色编码
     */
    public void setRoleCode(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>space_role.role_code</code>. 角色编码
     */
    public String getRoleCode() {
        return (String) get(2);
    }

    /**
     * Setter for <code>space_role.role_name</code>. 角色名称
     */
    public void setRoleName(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>space_role.role_name</code>. 角色名称
     */
    public String getRoleName() {
        return (String) get(3);
    }

    /**
     * Setter for <code>space_role.role_desc</code>. 角色描述
     */
    public void setRoleDesc(String value) {
        set(4, value);
    }

    /**
     * Getter for <code>space_role.role_desc</code>. 角色描述
     */
    public String getRoleDesc() {
        return (String) get(4);
    }

    /**
     * Setter for <code>space_role.role_type</code>. 角色类型(1系统内置，2自定义)
     */
    public void setRoleType(Byte value) {
        set(5, value);
    }

    /**
     * Getter for <code>space_role.role_type</code>. 角色类型(1系统内置，2自定义)
     */
    public Byte getRoleType() {
        return (Byte) get(5);
    }

    /**
     * Setter for <code>space_role.status</code>. 删除状态(0未删除，-1已删除)
     */
    public void setStatus(Byte value) {
        set(6, value);
    }

    /**
     * Getter for <code>space_role.status</code>. 删除状态(0未删除，-1已删除)
     */
    public Byte getStatus() {
        return (Byte) get(6);
    }

    /**
     * Setter for <code>space_role.ctime</code>. 创建时间
     */
    public void setCtime(LocalDateTime value) {
        set(7, value);
    }

    /**
     * Getter for <code>space_role.ctime</code>. 创建时间
     */
    public LocalDateTime getCtime() {
        return (LocalDateTime) get(7);
    }

    /**
     * Setter for <code>space_role.mtime</code>. 最后一次更新时间
     */
    public void setMtime(LocalDateTime value) {
        set(8, value);
    }

    /**
     * Getter for <code>space_role.mtime</code>. 最后一次更新时间
     */
    public LocalDateTime getMtime() {
        return (LocalDateTime) get(8);
    }

    /**
     * Setter for <code>space_role.cuid</code>. 创建人id
     */
    public void setCuid(Long value) {
        set(9, value);
    }

    /**
     * Getter for <code>space_role.cuid</code>. 创建人id
     */
    public Long getCuid() {
        return (Long) get(9);
    }

    /**
     * Setter for <code>space_role.muid</code>. 最后一次更新人id
     */
    public void setMuid(Long value) {
        set(10, value);
    }

    /**
     * Getter for <code>space_role.muid</code>. 最后一次更新人id
     */
    public Long getMuid() {
        return (Long) get(10);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record11 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row11<Long, String, String, String, String, Byte, Byte, LocalDateTime, LocalDateTime, Long, Long> fieldsRow() {
        return (Row11) super.fieldsRow();
    }

    @Override
    public Row11<Long, String, String, String, String, Byte, Byte, LocalDateTime, LocalDateTime, Long, Long> valuesRow() {
        return (Row11) super.valuesRow();
    }

    @Override
    public Field<Long> field1() {
        return SpaceRole.SPACE_ROLE.ID;
    }

    @Override
    public Field<String> field2() {
        return SpaceRole.SPACE_ROLE.SPACE_CODE;
    }

    @Override
    public Field<String> field3() {
        return SpaceRole.SPACE_ROLE.ROLE_CODE;
    }

    @Override
    public Field<String> field4() {
        return SpaceRole.SPACE_ROLE.ROLE_NAME;
    }

    @Override
    public Field<String> field5() {
        return SpaceRole.SPACE_ROLE.ROLE_DESC;
    }

    @Override
    public Field<Byte> field6() {
        return SpaceRole.SPACE_ROLE.ROLE_TYPE;
    }

    @Override
    public Field<Byte> field7() {
        return SpaceRole.SPACE_ROLE.STATUS;
    }

    @Override
    public Field<LocalDateTime> field8() {
        return SpaceRole.SPACE_ROLE.CTIME;
    }

    @Override
    public Field<LocalDateTime> field9() {
        return SpaceRole.SPACE_ROLE.MTIME;
    }

    @Override
    public Field<Long> field10() {
        return SpaceRole.SPACE_ROLE.CUID;
    }

    @Override
    public Field<Long> field11() {
        return SpaceRole.SPACE_ROLE.MUID;
    }

    @Override
    public Long component1() {
        return getId();
    }

    @Override
    public String component2() {
        return getSpaceCode();
    }

    @Override
    public String component3() {
        return getRoleCode();
    }

    @Override
    public String component4() {
        return getRoleName();
    }

    @Override
    public String component5() {
        return getRoleDesc();
    }

    @Override
    public Byte component6() {
        return getRoleType();
    }

    @Override
    public Byte component7() {
        return getStatus();
    }

    @Override
    public LocalDateTime component8() {
        return getCtime();
    }

    @Override
    public LocalDateTime component9() {
        return getMtime();
    }

    @Override
    public Long component10() {
        return getCuid();
    }

    @Override
    public Long component11() {
        return getMuid();
    }

    @Override
    public Long value1() {
        return getId();
    }

    @Override
    public String value2() {
        return getSpaceCode();
    }

    @Override
    public String value3() {
        return getRoleCode();
    }

    @Override
    public String value4() {
        return getRoleName();
    }

    @Override
    public String value5() {
        return getRoleDesc();
    }

    @Override
    public Byte value6() {
        return getRoleType();
    }

    @Override
    public Byte value7() {
        return getStatus();
    }

    @Override
    public LocalDateTime value8() {
        return getCtime();
    }

    @Override
    public LocalDateTime value9() {
        return getMtime();
    }

    @Override
    public Long value10() {
        return getCuid();
    }

    @Override
    public Long value11() {
        return getMuid();
    }

    @Override
    public SpaceRoleRecord value1(Long value) {
        setId(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value2(String value) {
        setSpaceCode(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value3(String value) {
        setRoleCode(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value4(String value) {
        setRoleName(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value5(String value) {
        setRoleDesc(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value6(Byte value) {
        setRoleType(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value7(Byte value) {
        setStatus(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value8(LocalDateTime value) {
        setCtime(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value9(LocalDateTime value) {
        setMtime(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value10(Long value) {
        setCuid(value);
        return this;
    }

    @Override
    public SpaceRoleRecord value11(Long value) {
        setMuid(value);
        return this;
    }

    @Override
    public SpaceRoleRecord values(Long value1, String value2, String value3, String value4, String value5, Byte value6, Byte value7, LocalDateTime value8, LocalDateTime value9, Long value10, Long value11) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        value9(value9);
        value10(value10);
        value11(value11);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached SpaceRoleRecord
     */
    public SpaceRoleRecord() {
        super(SpaceRole.SPACE_ROLE);
    }

    /**
     * Create a detached, initialised SpaceRoleRecord
     */
    public SpaceRoleRecord(Long id, String spaceCode, String roleCode, String roleName, String roleDesc, Byte roleType, Byte status, LocalDateTime ctime, LocalDateTime mtime, Long cuid, Long muid) {
        super(SpaceRole.SPACE_ROLE);

        setId(id);
        setSpaceCode(spaceCode);
        setRoleCode(roleCode);
        setRoleName(roleName);
        setRoleDesc(roleDesc);
        setRoleType(roleType);
        setStatus(status);
        setCtime(ctime);
        setMtime(mtime);
        setCuid(cuid);
        setMuid(muid);
    }
}
