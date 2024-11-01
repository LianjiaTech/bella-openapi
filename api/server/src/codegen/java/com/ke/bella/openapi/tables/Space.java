/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables;


import com.ke.bella.openapi.DefaultSchema;
import com.ke.bella.openapi.Keys;
import com.ke.bella.openapi.tables.records.SpaceRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
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
 * 空间表
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Space extends TableImpl<SpaceRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>space</code>
     */
    public static final Space SPACE = new Space();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SpaceRecord> getRecordType() {
        return SpaceRecord.class;
    }

    /**
     * The column <code>space.id</code>. 主键
     */
    public final TableField<SpaceRecord, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "主键");

    /**
     * The column <code>space.space_code</code>. 空间编码
     */
    public final TableField<SpaceRecord, String> SPACE_CODE = createField(DSL.name("space_code"), SQLDataType.VARCHAR(64).nullable(false).defaultValue(DSL.inline("", SQLDataType.VARCHAR)), this, "空间编码");

    /**
     * The column <code>space.space_name</code>. 空间名称
     */
    public final TableField<SpaceRecord, String> SPACE_NAME = createField(DSL.name("space_name"), SQLDataType.VARCHAR(128).nullable(false).defaultValue(DSL.inline("", SQLDataType.VARCHAR)), this, "空间名称");

    /**
     * The column <code>space.space_description</code>. 空间描述
     */
    public final TableField<SpaceRecord, String> SPACE_DESCRIPTION = createField(DSL.name("space_description"), SQLDataType.VARCHAR(255).nullable(false).defaultValue(DSL.inline("", SQLDataType.VARCHAR)), this, "空间描述");

    /**
     * The column <code>space.status</code>. 删除状态(0未删除，-1已删除)
     */
    public final TableField<SpaceRecord, Byte> STATUS = createField(DSL.name("status"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "删除状态(0未删除，-1已删除)");

    /**
     * The column <code>space.ctime</code>. 创建时间
     */
    public final TableField<SpaceRecord, LocalDateTime> CTIME = createField(DSL.name("ctime"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field("CURRENT_TIMESTAMP", SQLDataType.LOCALDATETIME)), this, "创建时间");

    /**
     * The column <code>space.mtime</code>. 最后一次更新时间
     */
    public final TableField<SpaceRecord, LocalDateTime> MTIME = createField(DSL.name("mtime"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field("CURRENT_TIMESTAMP", SQLDataType.LOCALDATETIME)), this, "最后一次更新时间");

    /**
     * The column <code>space.owner_uid</code>. 空间拥有人系统号
     */
    public final TableField<SpaceRecord, String> OWNER_UID = createField(DSL.name("owner_uid"), SQLDataType.VARCHAR(64).nullable(false).defaultValue(DSL.inline("", SQLDataType.VARCHAR)), this, "空间拥有人系统号");

    /**
     * The column <code>space.cuid</code>. 空间创建人系统号
     */
    public final TableField<SpaceRecord, Long> CUID = createField(DSL.name("cuid"), SQLDataType.BIGINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.BIGINT)), this, "空间创建人系统号");

    /**
     * The column <code>space.muid</code>. 空间最后一次更新人系统号
     */
    public final TableField<SpaceRecord, Long> MUID = createField(DSL.name("muid"), SQLDataType.BIGINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.BIGINT)), this, "空间最后一次更新人系统号");

    private Space(Name alias, Table<SpaceRecord> aliased) {
        this(alias, aliased, null);
    }

    private Space(Name alias, Table<SpaceRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment("空间表"), TableOptions.table());
    }

    /**
     * Create an aliased <code>space</code> table reference
     */
    public Space(String alias) {
        this(DSL.name(alias), SPACE);
    }

    /**
     * Create an aliased <code>space</code> table reference
     */
    public Space(Name alias) {
        this(alias, SPACE);
    }

    /**
     * Create a <code>space</code> table reference
     */
    public Space() {
        this(DSL.name("space"), null);
    }

    public <O extends Record> Space(Table<O> child, ForeignKey<O, SpaceRecord> key) {
        super(child, key, SPACE);
    }

    @Override
    public Schema getSchema() {
        return DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public Identity<SpaceRecord, Long> getIdentity() {
        return (Identity<SpaceRecord, Long>) super.getIdentity();
    }

    @Override
    public UniqueKey<SpaceRecord> getPrimaryKey() {
        return Keys.KEY_SPACE_PRIMARY;
    }

    @Override
    public List<UniqueKey<SpaceRecord>> getKeys() {
        return Arrays.<UniqueKey<SpaceRecord>>asList(Keys.KEY_SPACE_PRIMARY, Keys.KEY_SPACE_UNIQ_IDX_SPACE_CODE);
    }

    @Override
    public Space as(String alias) {
        return new Space(DSL.name(alias), this);
    }

    @Override
    public Space as(Name alias) {
        return new Space(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Space rename(String name) {
        return new Space(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Space rename(Name name) {
        return new Space(name, null);
    }

    // -------------------------------------------------------------------------
    // Row10 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row10<Long, String, String, String, Byte, LocalDateTime, LocalDateTime, String, Long, Long> fieldsRow() {
        return (Row10) super.fieldsRow();
    }
}
