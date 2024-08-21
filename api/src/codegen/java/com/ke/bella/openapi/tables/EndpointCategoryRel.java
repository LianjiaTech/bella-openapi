/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables;


import com.ke.bella.openapi.DefaultSchema;
import com.ke.bella.openapi.Indexes;
import com.ke.bella.openapi.Keys;
import com.ke.bella.openapi.tables.records.EndpointCategoryRelRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row10;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * 能力点类目
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class EndpointCategoryRel extends TableImpl<EndpointCategoryRelRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>endpoint_category_rel</code>
     */
    public static final EndpointCategoryRel ENDPOINT_CATEGORY_REL = new EndpointCategoryRel();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<EndpointCategoryRelRecord> getRecordType() {
        return EndpointCategoryRelRecord.class;
    }

    /**
     * The column <code>endpoint_category_rel.id</code>. 主键ID
     */
    public final TableField<EndpointCategoryRelRecord, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "主键ID");

    /**
     * The column <code>endpoint_category_rel.endpoint</code>. 能力点
     */
    public final TableField<EndpointCategoryRelRecord, String> ENDPOINT = createField(DSL.name("endpoint"), SQLDataType.VARCHAR(64).nullable(false).defaultValue(DSL.inline("", SQLDataType.VARCHAR)), this, "能力点");

    /**
     * The column <code>endpoint_category_rel.category_code</code>. 类目编码
     */
    public final TableField<EndpointCategoryRelRecord, String> CATEGORY_CODE = createField(DSL.name("category_code"), SQLDataType.VARCHAR(128).nullable(false).defaultValue(DSL.inline("", SQLDataType.VARCHAR)), this, "类目编码");

    /**
     * The column <code>endpoint_category_rel.sort</code>. 排序
     */
    public final TableField<EndpointCategoryRelRecord, Integer> SORT = createField(DSL.name("sort"), SQLDataType.INTEGER.nullable(false).defaultValue(DSL.inline("0", SQLDataType.INTEGER)), this, "排序");

    /**
     * The column <code>endpoint_category_rel.cuid</code>. 创建人id
     */
    public final TableField<EndpointCategoryRelRecord, Long> CUID = createField(DSL.name("cuid"), SQLDataType.BIGINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.BIGINT)), this, "创建人id");

    /**
     * The column <code>endpoint_category_rel.cu_name</code>. 创建人姓名
     */
    public final TableField<EndpointCategoryRelRecord, String> CU_NAME = createField(DSL.name("cu_name"), SQLDataType.VARCHAR(16).nullable(false).defaultValue(DSL.inline("", SQLDataType.VARCHAR)), this, "创建人姓名");

    /**
     * The column <code>endpoint_category_rel.muid</code>. 编辑人id
     */
    public final TableField<EndpointCategoryRelRecord, Long> MUID = createField(DSL.name("muid"), SQLDataType.BIGINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.BIGINT)), this, "编辑人id");

    /**
     * The column <code>endpoint_category_rel.mu_name</code>. 编辑人姓名
     */
    public final TableField<EndpointCategoryRelRecord, String> MU_NAME = createField(DSL.name("mu_name"), SQLDataType.VARCHAR(16).nullable(false).defaultValue(DSL.inline("", SQLDataType.VARCHAR)), this, "编辑人姓名");

    /**
     * The column <code>endpoint_category_rel.ctime</code>.
     */
    public final TableField<EndpointCategoryRelRecord, LocalDateTime> CTIME = createField(DSL.name("ctime"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field("CURRENT_TIMESTAMP", SQLDataType.LOCALDATETIME)), this, "");

    /**
     * The column <code>endpoint_category_rel.mtime</code>.
     */
    public final TableField<EndpointCategoryRelRecord, LocalDateTime> MTIME = createField(DSL.name("mtime"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field("CURRENT_TIMESTAMP", SQLDataType.LOCALDATETIME)), this, "");

    private EndpointCategoryRel(Name alias, Table<EndpointCategoryRelRecord> aliased) {
        this(alias, aliased, null);
    }

    private EndpointCategoryRel(Name alias, Table<EndpointCategoryRelRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment("能力点类目"), TableOptions.table());
    }

    /**
     * Create an aliased <code>endpoint_category_rel</code> table reference
     */
    public EndpointCategoryRel(String alias) {
        this(DSL.name(alias), ENDPOINT_CATEGORY_REL);
    }

    /**
     * Create an aliased <code>endpoint_category_rel</code> table reference
     */
    public EndpointCategoryRel(Name alias) {
        this(alias, ENDPOINT_CATEGORY_REL);
    }

    /**
     * Create a <code>endpoint_category_rel</code> table reference
     */
    public EndpointCategoryRel() {
        this(DSL.name("endpoint_category_rel"), null);
    }

    public <O extends Record> EndpointCategoryRel(Table<O> child, ForeignKey<O, EndpointCategoryRelRecord> key) {
        super(child, key, ENDPOINT_CATEGORY_REL);
    }

    @Override
    public Schema getSchema() {
        return DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.ENDPOINT_CATEGORY_REL_IDX_CATEGORY_CODE, Indexes.ENDPOINT_CATEGORY_REL_IDX_SORT);
    }

    @Override
    public Identity<EndpointCategoryRelRecord, Long> getIdentity() {
        return (Identity<EndpointCategoryRelRecord, Long>) super.getIdentity();
    }

    @Override
    public UniqueKey<EndpointCategoryRelRecord> getPrimaryKey() {
        return Keys.KEY_ENDPOINT_CATEGORY_REL_PRIMARY;
    }

    @Override
    public List<UniqueKey<EndpointCategoryRelRecord>> getKeys() {
        return Arrays.<UniqueKey<EndpointCategoryRelRecord>>asList(Keys.KEY_ENDPOINT_CATEGORY_REL_PRIMARY, Keys.KEY_ENDPOINT_CATEGORY_REL_UNIQ_IDX_UNI_ENDPOINT_CATEGORY_CODE);
    }

    @Override
    public EndpointCategoryRel as(String alias) {
        return new EndpointCategoryRel(DSL.name(alias), this);
    }

    @Override
    public EndpointCategoryRel as(Name alias) {
        return new EndpointCategoryRel(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public EndpointCategoryRel rename(String name) {
        return new EndpointCategoryRel(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public EndpointCategoryRel rename(Name name) {
        return new EndpointCategoryRel(name, null);
    }

    // -------------------------------------------------------------------------
    // Row10 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row10<Long, String, String, Integer, Long, String, Long, String, LocalDateTime, LocalDateTime> fieldsRow() {
        return (Row10) super.fieldsRow();
    }
}
