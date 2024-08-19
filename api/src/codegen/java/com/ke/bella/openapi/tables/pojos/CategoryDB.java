/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables.pojos;


import com.ke.bella.openapi.db.repo.Operator;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * openapi类目
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class CategoryDB implements Operator, Serializable {

    private static final long serialVersionUID = 1L;

    private Long          id;
    private String        categoryCode;
    private String        categoryName;
    private String        parentCode;
    private String        status;
    private Long          cuid;
    private String        cuName;
    private Long          muid;
    private String        muName;
    private LocalDateTime ctime;
    private LocalDateTime mtime;

    public CategoryDB() {}

    public CategoryDB(CategoryDB value) {
        this.id = value.id;
        this.categoryCode = value.categoryCode;
        this.categoryName = value.categoryName;
        this.parentCode = value.parentCode;
        this.status = value.status;
        this.cuid = value.cuid;
        this.cuName = value.cuName;
        this.muid = value.muid;
        this.muName = value.muName;
        this.ctime = value.ctime;
        this.mtime = value.mtime;
    }

    public CategoryDB(
        Long          id,
        String        categoryCode,
        String        categoryName,
        String        parentCode,
        String        status,
        Long          cuid,
        String        cuName,
        Long          muid,
        String        muName,
        LocalDateTime ctime,
        LocalDateTime mtime
    ) {
        this.id = id;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.parentCode = parentCode;
        this.status = status;
        this.cuid = cuid;
        this.cuName = cuName;
        this.muid = muid;
        this.muName = muName;
        this.ctime = ctime;
        this.mtime = mtime;
    }

    /**
     * Getter for <code>category.id</code>. 主键ID
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Setter for <code>category.id</code>. 主键ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Getter for <code>category.category_code</code>. 类目编码
     */
    public String getCategoryCode() {
        return this.categoryCode;
    }

    /**
     * Setter for <code>category.category_code</code>. 类目编码
     */
    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    /**
     * Getter for <code>category.category_name</code>. 类目名
     */
    public String getCategoryName() {
        return this.categoryName;
    }

    /**
     * Setter for <code>category.category_name</code>. 类目名
     */
    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    /**
     * Getter for <code>category.parent_code</code>. 父类目编码
     */
    public String getParentCode() {
        return this.parentCode;
    }

    /**
     * Setter for <code>category.parent_code</code>. 父类目编码
     */
    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    /**
     * Getter for <code>category.status</code>. 状态(active/inactive)
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Setter for <code>category.status</code>. 状态(active/inactive)
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Getter for <code>category.cuid</code>. 创建人id
     */
    public Long getCuid() {
        return this.cuid;
    }

    /**
     * Setter for <code>category.cuid</code>. 创建人id
     */
    public void setCuid(Long cuid) {
        this.cuid = cuid;
    }

    /**
     * Getter for <code>category.cu_name</code>. 创建人姓名
     */
    public String getCuName() {
        return this.cuName;
    }

    /**
     * Setter for <code>category.cu_name</code>. 创建人姓名
     */
    public void setCuName(String cuName) {
        this.cuName = cuName;
    }

    /**
     * Getter for <code>category.muid</code>. 编辑人id
     */
    public Long getMuid() {
        return this.muid;
    }

    /**
     * Setter for <code>category.muid</code>. 编辑人id
     */
    public void setMuid(Long muid) {
        this.muid = muid;
    }

    /**
     * Getter for <code>category.mu_name</code>. 编辑人姓名
     */
    public String getMuName() {
        return this.muName;
    }

    /**
     * Setter for <code>category.mu_name</code>. 编辑人姓名
     */
    public void setMuName(String muName) {
        this.muName = muName;
    }

    /**
     * Getter for <code>category.ctime</code>.
     */
    public LocalDateTime getCtime() {
        return this.ctime;
    }

    /**
     * Setter for <code>category.ctime</code>.
     */
    public void setCtime(LocalDateTime ctime) {
        this.ctime = ctime;
    }

    /**
     * Getter for <code>category.mtime</code>.
     */
    public LocalDateTime getMtime() {
        return this.mtime;
    }

    /**
     * Setter for <code>category.mtime</code>.
     */
    public void setMtime(LocalDateTime mtime) {
        this.mtime = mtime;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CategoryDB (");

        sb.append(id);
        sb.append(", ").append(categoryCode);
        sb.append(", ").append(categoryName);
        sb.append(", ").append(parentCode);
        sb.append(", ").append(status);
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
