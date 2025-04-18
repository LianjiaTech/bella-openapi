/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables.pojos;


import com.ke.bella.openapi.db.repo.Operator;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * ak角色
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ApikeyRoleDB implements Operator, Serializable {

    private static final long serialVersionUID = 1L;

    private Long          id;
    private String        roleCode;
    private String        path;
    private Long          cuid;
    private String        cuName;
    private Long          muid;
    private String        muName;
    private LocalDateTime ctime;
    private LocalDateTime mtime;

    public ApikeyRoleDB() {}

    public ApikeyRoleDB(ApikeyRoleDB value) {
        this.id = value.id;
        this.roleCode = value.roleCode;
        this.path = value.path;
        this.cuid = value.cuid;
        this.cuName = value.cuName;
        this.muid = value.muid;
        this.muName = value.muName;
        this.ctime = value.ctime;
        this.mtime = value.mtime;
    }

    public ApikeyRoleDB(
        Long          id,
        String        roleCode,
        String        path,
        Long          cuid,
        String        cuName,
        Long          muid,
        String        muName,
        LocalDateTime ctime,
        LocalDateTime mtime
    ) {
        this.id = id;
        this.roleCode = roleCode;
        this.path = path;
        this.cuid = cuid;
        this.cuName = cuName;
        this.muid = muid;
        this.muName = muName;
        this.ctime = ctime;
        this.mtime = mtime;
    }

    /**
     * Getter for <code>apikey_role.id</code>. 主键ID
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Setter for <code>apikey_role.id</code>. 主键ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Getter for <code>apikey_role.role_code</code>. ak编码
     */
    public String getRoleCode() {
        return this.roleCode;
    }

    /**
     * Setter for <code>apikey_role.role_code</code>. ak编码
     */
    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    /**
     * Getter for <code>apikey_role.path</code>. 授权的path
     */
    public String getPath() {
        return this.path;
    }

    /**
     * Setter for <code>apikey_role.path</code>. 授权的path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Getter for <code>apikey_role.cuid</code>. 创建人id
     */
    public Long getCuid() {
        return this.cuid;
    }

    /**
     * Setter for <code>apikey_role.cuid</code>. 创建人id
     */
    public void setCuid(Long cuid) {
        this.cuid = cuid;
    }

    /**
     * Getter for <code>apikey_role.cu_name</code>. 创建人姓名
     */
    public String getCuName() {
        return this.cuName;
    }

    /**
     * Setter for <code>apikey_role.cu_name</code>. 创建人姓名
     */
    public void setCuName(String cuName) {
        this.cuName = cuName;
    }

    /**
     * Getter for <code>apikey_role.muid</code>. 编辑人id
     */
    public Long getMuid() {
        return this.muid;
    }

    /**
     * Setter for <code>apikey_role.muid</code>. 编辑人id
     */
    public void setMuid(Long muid) {
        this.muid = muid;
    }

    /**
     * Getter for <code>apikey_role.mu_name</code>. 编辑人姓名
     */
    public String getMuName() {
        return this.muName;
    }

    /**
     * Setter for <code>apikey_role.mu_name</code>. 编辑人姓名
     */
    public void setMuName(String muName) {
        this.muName = muName;
    }

    /**
     * Getter for <code>apikey_role.ctime</code>.
     */
    public LocalDateTime getCtime() {
        return this.ctime;
    }

    /**
     * Setter for <code>apikey_role.ctime</code>.
     */
    public void setCtime(LocalDateTime ctime) {
        this.ctime = ctime;
    }

    /**
     * Getter for <code>apikey_role.mtime</code>.
     */
    public LocalDateTime getMtime() {
        return this.mtime;
    }

    /**
     * Setter for <code>apikey_role.mtime</code>.
     */
    public void setMtime(LocalDateTime mtime) {
        this.mtime = mtime;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ApikeyRoleDB (");

        sb.append(id);
        sb.append(", ").append(roleCode);
        sb.append(", ").append(path);
        sb.append(", ").append(cuid);
        sb.append(", ").append(cuName);
        sb.append(", ").append(muid);
        sb.append(", ").append(muName);
        sb.append(", ").append(ctime);
        sb.append(", ").append(mtime);

        sb.append(")");
        return sb.toString();
    }
}
