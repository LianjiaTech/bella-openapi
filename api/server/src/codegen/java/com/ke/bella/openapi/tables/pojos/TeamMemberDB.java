/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables.pojos;


import com.ke.bella.openapi.db.repo.Operator;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * 团队成员信息表
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class TeamMemberDB implements Operator, Serializable {

    private static final long serialVersionUID = 1L;

    private Long          id;
    private String        teamCode;
    private String        roleCode;
    private Long          memberUid;
    private String        memberName;
    private Byte          status;
    private LocalDateTime ctime;
    private LocalDateTime mtime;
    private Long          cuid;
    private Long          muid;
    private String        cuName;
    private String        muName;

    public TeamMemberDB() {}

    public TeamMemberDB(TeamMemberDB value) {
        this.id = value.id;
        this.teamCode = value.teamCode;
        this.roleCode = value.roleCode;
        this.memberUid = value.memberUid;
        this.memberName = value.memberName;
        this.status = value.status;
        this.ctime = value.ctime;
        this.mtime = value.mtime;
        this.cuid = value.cuid;
        this.muid = value.muid;
        this.cuName = value.cuName;
        this.muName = value.muName;
    }

    public TeamMemberDB(
        Long          id,
        String        teamCode,
        String        roleCode,
        Long          memberUid,
        String        memberName,
        Byte          status,
        LocalDateTime ctime,
        LocalDateTime mtime,
        Long          cuid,
        Long          muid,
        String        cuName,
        String        muName
    ) {
        this.id = id;
        this.teamCode = teamCode;
        this.roleCode = roleCode;
        this.memberUid = memberUid;
        this.memberName = memberName;
        this.status = status;
        this.ctime = ctime;
        this.mtime = mtime;
        this.cuid = cuid;
        this.muid = muid;
        this.cuName = cuName;
        this.muName = muName;
    }

    /**
     * Getter for <code>team_member.id</code>. 主键
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Setter for <code>team_member.id</code>. 主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Getter for <code>team_member.team_code</code>. 团队编码
     */
    public String getTeamCode() {
        return this.teamCode;
    }

    /**
     * Setter for <code>team_member.team_code</code>. 团队编码
     */
    public void setTeamCode(String teamCode) {
        this.teamCode = teamCode;
    }

    /**
     * Getter for <code>team_member.role_code</code>. 角色编码
     */
    public String getRoleCode() {
        return this.roleCode;
    }

    /**
     * Setter for <code>team_member.role_code</code>. 角色编码
     */
    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    /**
     * Getter for <code>team_member.member_uid</code>. 成员系统号
     */
    public Long getMemberUid() {
        return this.memberUid;
    }

    /**
     * Setter for <code>team_member.member_uid</code>. 成员系统号
     */
    public void setMemberUid(Long memberUid) {
        this.memberUid = memberUid;
    }

    /**
     * Getter for <code>team_member.member_name</code>. 成员用户名
     */
    public String getMemberName() {
        return this.memberName;
    }

    /**
     * Setter for <code>team_member.member_name</code>. 成员用户名
     */
    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    /**
     * Getter for <code>team_member.status</code>. 删除状态(0未删除，-1已删除)
     */
    public Byte getStatus() {
        return this.status;
    }

    /**
     * Setter for <code>team_member.status</code>. 删除状态(0未删除，-1已删除)
     */
    public void setStatus(Byte status) {
        this.status = status;
    }

    /**
     * Getter for <code>team_member.ctime</code>. 创建时间
     */
    public LocalDateTime getCtime() {
        return this.ctime;
    }

    /**
     * Setter for <code>team_member.ctime</code>. 创建时间
     */
    public void setCtime(LocalDateTime ctime) {
        this.ctime = ctime;
    }

    /**
     * Getter for <code>team_member.mtime</code>. 最后一次修改时间
     */
    public LocalDateTime getMtime() {
        return this.mtime;
    }

    /**
     * Setter for <code>team_member.mtime</code>. 最后一次修改时间
     */
    public void setMtime(LocalDateTime mtime) {
        this.mtime = mtime;
    }

    /**
     * Getter for <code>team_member.cuid</code>. 创建人系统号
     */
    public Long getCuid() {
        return this.cuid;
    }

    /**
     * Setter for <code>team_member.cuid</code>. 创建人系统号
     */
    public void setCuid(Long cuid) {
        this.cuid = cuid;
    }

    /**
     * Getter for <code>team_member.muid</code>. 最后一次更新人系统号
     */
    public Long getMuid() {
        return this.muid;
    }

    /**
     * Setter for <code>team_member.muid</code>. 最后一次更新人系统号
     */
    public void setMuid(Long muid) {
        this.muid = muid;
    }

    /**
     * Getter for <code>team_member.cu_name</code>. 团队创建人姓名
     */
    public String getCuName() {
        return this.cuName;
    }

    /**
     * Setter for <code>team_member.cu_name</code>. 团队创建人姓名
     */
    public void setCuName(String cuName) {
        this.cuName = cuName;
    }

    /**
     * Getter for <code>team_member.mu_name</code>. 团队最后一次更新人姓名
     */
    public String getMuName() {
        return this.muName;
    }

    /**
     * Setter for <code>team_member.mu_name</code>. 团队最后一次更新人姓名
     */
    public void setMuName(String muName) {
        this.muName = muName;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TeamMemberDB (");

        sb.append(id);
        sb.append(", ").append(teamCode);
        sb.append(", ").append(roleCode);
        sb.append(", ").append(memberUid);
        sb.append(", ").append(memberName);
        sb.append(", ").append(status);
        sb.append(", ").append(ctime);
        sb.append(", ").append(mtime);
        sb.append(", ").append(cuid);
        sb.append(", ").append(muid);
        sb.append(", ").append(cuName);
        sb.append(", ").append(muName);

        sb.append(")");
        return sb.toString();
    }
}
