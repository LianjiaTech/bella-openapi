/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables;


import com.ke.bella.openapi.DefaultSchema;
import com.ke.bella.openapi.Indexes;
import com.ke.bella.openapi.Keys;
import com.ke.bella.openapi.tables.records.CategoryRecord;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;


/**
 * openapi类目
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.12"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Category extends TableImpl<CategoryRecord> {

    private static final long serialVersionUID = 652881237;

    /**
     * The reference instance of <code>category</code>
     */
    public static final Category CATEGORY = new Category();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<CategoryRecord> getRecordType() {
        return CategoryRecord.class;
    }

    /**
     * The column <code>category.id</code>. 主键ID
     */
    public final TableField<CategoryRecord, Long> ID = createField("id", org.jooq.impl.SQLDataType.BIGINT.nullable(false).identity(true), this, "主键ID");

    /**
     * The column <code>category.category_code</code>. 类目编码
     */
    public final TableField<CategoryRecord, String> CATEGORY_CODE = createField("category_code", org.jooq.impl.SQLDataType.VARCHAR(128).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "类目编码");

    /**
     * The column <code>category.category_name</code>. 类目名
     */
    public final TableField<CategoryRecord, String> CATEGORY_NAME = createField("category_name", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "类目名");

    /**
     * The column <code>category.parent_code</code>. 父类目编码
     */
    public final TableField<CategoryRecord, String> PARENT_CODE = createField("parent_code", org.jooq.impl.SQLDataType.VARCHAR(128).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "父类目编码");

    /**
     * The column <code>category.status</code>. 状态(active/inactive)
     */
    public final TableField<CategoryRecord, String> STATUS = createField("status", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("active", org.jooq.impl.SQLDataType.VARCHAR)), this, "状态(active/inactive)");

    /**
     * The column <code>category.cuid</code>. 创建人id
     */
    public final TableField<CategoryRecord, Long> CUID = createField("cuid", org.jooq.impl.SQLDataType.BIGINT.nullable(false).defaultValue(org.jooq.impl.DSL.inline("0", org.jooq.impl.SQLDataType.BIGINT)), this, "创建人id");

    /**
     * The column <code>category.cu_name</code>. 创建人姓名
     */
    public final TableField<CategoryRecord, String> CU_NAME = createField("cu_name", org.jooq.impl.SQLDataType.VARCHAR(16).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "创建人姓名");

    /**
     * The column <code>category.muid</code>. 编辑人id
     */
    public final TableField<CategoryRecord, Long> MUID = createField("muid", org.jooq.impl.SQLDataType.BIGINT.nullable(false).defaultValue(org.jooq.impl.DSL.inline("0", org.jooq.impl.SQLDataType.BIGINT)), this, "编辑人id");

    /**
     * The column <code>category.mu_name</code>. 编辑人姓名
     */
    public final TableField<CategoryRecord, String> MU_NAME = createField("mu_name", org.jooq.impl.SQLDataType.VARCHAR(16).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "编辑人姓名");

    /**
     * The column <code>category.ctime</code>.
     */
    public final TableField<CategoryRecord, Timestamp> CTIME = createField("ctime", org.jooq.impl.SQLDataType.TIMESTAMP.nullable(false).defaultValue(org.jooq.impl.DSL.field("CURRENT_TIMESTAMP", org.jooq.impl.SQLDataType.TIMESTAMP)), this, "");

    /**
     * The column <code>category.mtime</code>.
     */
    public final TableField<CategoryRecord, Timestamp> MTIME = createField("mtime", org.jooq.impl.SQLDataType.TIMESTAMP.nullable(false).defaultValue(org.jooq.impl.DSL.field("CURRENT_TIMESTAMP", org.jooq.impl.SQLDataType.TIMESTAMP)), this, "");

    /**
     * Create a <code>category</code> table reference
     */
    public Category() {
        this(DSL.name("category"), null);
    }

    /**
     * Create an aliased <code>category</code> table reference
     */
    public Category(String alias) {
        this(DSL.name(alias), CATEGORY);
    }

    /**
     * Create an aliased <code>category</code> table reference
     */
    public Category(Name alias) {
        this(alias, CATEGORY);
    }

    private Category(Name alias, Table<CategoryRecord> aliased) {
        this(alias, aliased, null);
    }

    private Category(Name alias, Table<CategoryRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment("openapi类目"));
    }

    public <O extends Record> Category(Table<O> child, ForeignKey<O, CategoryRecord> key) {
        super(child, key, CATEGORY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return DefaultSchema.DEFAULT_SCHEMA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.CATEGORY_IDX_CATEGORY_NAME, Indexes.CATEGORY_PRIMARY, Indexes.CATEGORY_UNIQ_IDX_PARENT_CODE_CATEGORY_NAME, Indexes.CATEGORY_UNIQ_IDX_UNI_CATEGORY_CODE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity<CategoryRecord, Long> getIdentity() {
        return Keys.IDENTITY_CATEGORY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<CategoryRecord> getPrimaryKey() {
        return Keys.KEY_CATEGORY_PRIMARY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<CategoryRecord>> getKeys() {
        return Arrays.<UniqueKey<CategoryRecord>>asList(Keys.KEY_CATEGORY_PRIMARY, Keys.KEY_CATEGORY_UNIQ_IDX_UNI_CATEGORY_CODE, Keys.KEY_CATEGORY_UNIQ_IDX_PARENT_CODE_CATEGORY_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Category as(String alias) {
        return new Category(DSL.name(alias), this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Category as(Name alias) {
        return new Category(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Category rename(String name) {
        return new Category(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Category rename(Name name) {
        return new Category(name, null);
    }
}
