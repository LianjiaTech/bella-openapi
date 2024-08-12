/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi.tables;


import com.ke.bella.openapi.DefaultSchema;
import com.ke.bella.openapi.Indexes;
import com.ke.bella.openapi.Keys;
import com.ke.bella.openapi.tables.records.ChannelRecord;

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
 * openapi通道
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.12"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Channel extends TableImpl<ChannelRecord> {

    private static final long serialVersionUID = 1826007917;

    /**
     * The reference instance of <code>channel</code>
     */
    public static final Channel CHANNEL = new Channel();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<ChannelRecord> getRecordType() {
        return ChannelRecord.class;
    }

    /**
     * The column <code>channel.id</code>. 主键ID
     */
    public final TableField<ChannelRecord, Long> ID = createField("id", org.jooq.impl.SQLDataType.BIGINT.nullable(false).identity(true), this, "主键ID");

    /**
     * The column <code>channel.entity_type</code>. 实体类型（endpoint/model）
     */
    public final TableField<ChannelRecord, String> ENTITY_TYPE = createField("entity_type", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("model", org.jooq.impl.SQLDataType.VARCHAR)), this, "实体类型（endpoint/model）");

    /**
     * The column <code>channel.entity_code</code>. 实体编码
     */
    public final TableField<ChannelRecord, String> ENTITY_CODE = createField("entity_code", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "实体编码");

    /**
     * The column <code>channel.channel_code</code>. 渠道编码
     */
    public final TableField<ChannelRecord, String> CHANNEL_CODE = createField("channel_code", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "渠道编码");

    /**
     * The column <code>channel.status</code>. 状态状态(active/inactive)
     */
    public final TableField<ChannelRecord, String> STATUS = createField("status", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("active", org.jooq.impl.SQLDataType.VARCHAR)), this, "状态状态(active/inactive)");

    /**
     * The column <code>channel.data_destination</code>. 数据流向(inner/mainland/overseas)
     */
    public final TableField<ChannelRecord, String> DATA_DESTINATION = createField("data_destination", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("inner", org.jooq.impl.SQLDataType.VARCHAR)), this, "数据流向(inner/mainland/overseas)");

    /**
     * The column <code>channel.priority</code>. 优先级(high/normal/low)
     */
    public final TableField<ChannelRecord, String> PRIORITY = createField("priority", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("normal", org.jooq.impl.SQLDataType.VARCHAR)), this, "优先级(high/normal/low)");

    /**
     * The column <code>channel.protocol</code>. 协议
     */
    public final TableField<ChannelRecord, String> PROTOCOL = createField("protocol", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "协议");

    /**
     * The column <code>channel.supplier</code>. 服务商
     */
    public final TableField<ChannelRecord, String> SUPPLIER = createField("supplier", org.jooq.impl.SQLDataType.VARCHAR(64).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "服务商");

    /**
     * The column <code>channel.url</code>. 请求通道的url
     */
    public final TableField<ChannelRecord, String> URL = createField("url", org.jooq.impl.SQLDataType.VARCHAR(512).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "请求通道的url");

    /**
     * The column <code>channel.channel_info</code>. 渠道信息
     */
    public final TableField<ChannelRecord, String> CHANNEL_INFO = createField("channel_info", org.jooq.impl.SQLDataType.VARCHAR(512).nullable(false).defaultValue(org.jooq.impl.DSL.inline("{}", org.jooq.impl.SQLDataType.VARCHAR)), this, "渠道信息");

    /**
     * The column <code>channel.price_info</code>. 单价
     */
    public final TableField<ChannelRecord, String> PRICE_INFO = createField("price_info", org.jooq.impl.SQLDataType.VARCHAR(256).nullable(false).defaultValue(org.jooq.impl.DSL.inline("{}", org.jooq.impl.SQLDataType.VARCHAR)), this, "单价");

    /**
     * The column <code>channel.cuid</code>. 创建人id
     */
    public final TableField<ChannelRecord, Long> CUID = createField("cuid", org.jooq.impl.SQLDataType.BIGINT.nullable(false).defaultValue(org.jooq.impl.DSL.inline("0", org.jooq.impl.SQLDataType.BIGINT)), this, "创建人id");

    /**
     * The column <code>channel.cu_name</code>. 创建人姓名
     */
    public final TableField<ChannelRecord, String> CU_NAME = createField("cu_name", org.jooq.impl.SQLDataType.VARCHAR(16).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "创建人姓名");

    /**
     * The column <code>channel.muid</code>. 编辑人id
     */
    public final TableField<ChannelRecord, Long> MUID = createField("muid", org.jooq.impl.SQLDataType.BIGINT.nullable(false).defaultValue(org.jooq.impl.DSL.inline("0", org.jooq.impl.SQLDataType.BIGINT)), this, "编辑人id");

    /**
     * The column <code>channel.mu_name</code>. 编辑人姓名
     */
    public final TableField<ChannelRecord, String> MU_NAME = createField("mu_name", org.jooq.impl.SQLDataType.VARCHAR(16).nullable(false).defaultValue(org.jooq.impl.DSL.inline("", org.jooq.impl.SQLDataType.VARCHAR)), this, "编辑人姓名");

    /**
     * The column <code>channel.ctime</code>.
     */
    public final TableField<ChannelRecord, Timestamp> CTIME = createField("ctime", org.jooq.impl.SQLDataType.TIMESTAMP.nullable(false).defaultValue(org.jooq.impl.DSL.field("CURRENT_TIMESTAMP", org.jooq.impl.SQLDataType.TIMESTAMP)), this, "");

    /**
     * The column <code>channel.mtime</code>.
     */
    public final TableField<ChannelRecord, Timestamp> MTIME = createField("mtime", org.jooq.impl.SQLDataType.TIMESTAMP.nullable(false).defaultValue(org.jooq.impl.DSL.field("CURRENT_TIMESTAMP", org.jooq.impl.SQLDataType.TIMESTAMP)), this, "");

    /**
     * Create a <code>channel</code> table reference
     */
    public Channel() {
        this(DSL.name("channel"), null);
    }

    /**
     * Create an aliased <code>channel</code> table reference
     */
    public Channel(String alias) {
        this(DSL.name(alias), CHANNEL);
    }

    /**
     * Create an aliased <code>channel</code> table reference
     */
    public Channel(Name alias) {
        this(alias, CHANNEL);
    }

    private Channel(Name alias, Table<ChannelRecord> aliased) {
        this(alias, aliased, null);
    }

    private Channel(Name alias, Table<ChannelRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment("openapi通道"));
    }

    public <O extends Record> Channel(Table<O> child, ForeignKey<O, ChannelRecord> key) {
        super(child, key, CHANNEL);
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
        return Arrays.<Index>asList(Indexes.CHANNEL_IDX_ENTITY_TYPE_CODE, Indexes.CHANNEL_IDX_PROTOCOL, Indexes.CHANNEL_IDX_SUPPLIER, Indexes.CHANNEL_PRIMARY, Indexes.CHANNEL_UNIQ_IDX_UNI_CHANNEL_CODE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity<ChannelRecord, Long> getIdentity() {
        return Keys.IDENTITY_CHANNEL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<ChannelRecord> getPrimaryKey() {
        return Keys.KEY_CHANNEL_PRIMARY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<ChannelRecord>> getKeys() {
        return Arrays.<UniqueKey<ChannelRecord>>asList(Keys.KEY_CHANNEL_PRIMARY, Keys.KEY_CHANNEL_UNIQ_IDX_UNI_CHANNEL_CODE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Channel as(String alias) {
        return new Channel(DSL.name(alias), this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Channel as(Name alias) {
        return new Channel(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Channel rename(String name) {
        return new Channel(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Channel rename(Name name) {
        return new Channel(name, null);
    }
}
