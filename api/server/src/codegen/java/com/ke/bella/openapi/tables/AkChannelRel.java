package com.ke.bella.openapi.tables;

import com.ke.bella.openapi.DefaultSchema;
import com.ke.bella.openapi.Indexes;
import com.ke.bella.openapi.Keys;
import com.ke.bella.openapi.tables.records.AkChannelRelRecord;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row9;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class AkChannelRel extends TableImpl<AkChannelRelRecord> {
    private static final long serialVersionUID = 1L;
    public static final AkChannelRel AK_CHANNEL_REL = new AkChannelRel();

    @Override
    public Class<AkChannelRelRecord> getRecordType() {
        return AkChannelRelRecord.class;
    }

    public final TableField<AkChannelRelRecord, Long> ID =
            createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "主键ID");
    public final TableField<AkChannelRelRecord, String> AK_CODE =
            createField(DSL.name("ak_code"), SQLDataType.VARCHAR(64).nullable(false), this, "AK编码");
    public final TableField<AkChannelRelRecord, String> CHANNEL_CODE =
            createField(DSL.name("channel_code"), SQLDataType.VARCHAR(64).nullable(false), this, "渠道编码");
    public final TableField<AkChannelRelRecord, Long> CUID =
            createField(DSL.name("cuid"), SQLDataType.BIGINT.nullable(false), this, "创建人id");
    public final TableField<AkChannelRelRecord, String> CU_NAME =
            createField(DSL.name("cu_name"), SQLDataType.VARCHAR(16).nullable(false), this, "创建人姓名");
    public final TableField<AkChannelRelRecord, Long> MUID =
            createField(DSL.name("muid"), SQLDataType.BIGINT.nullable(false), this, "编辑人id");
    public final TableField<AkChannelRelRecord, String> MU_NAME =
            createField(DSL.name("mu_name"), SQLDataType.VARCHAR(16).nullable(false), this, "编辑人姓名");
    public final TableField<AkChannelRelRecord, LocalDateTime> CTIME =
            createField(DSL.name("ctime"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "");
    public final TableField<AkChannelRelRecord, LocalDateTime> MTIME =
            createField(DSL.name("mtime"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "");

    private AkChannelRel(Name alias, Table<AkChannelRelRecord> aliased) {
        super(alias, null, aliased, null, DSL.comment("AK与专用渠道关联"), TableOptions.table());
    }

    public AkChannelRel(String alias) {
        this(DSL.name(alias), AK_CHANNEL_REL);
    }

    public AkChannelRel(Name alias) {
        this(alias, AK_CHANNEL_REL);
    }

    public AkChannelRel() {
        this(DSL.name("ak_channel_rel"), null);
    }

    public <O extends Record> AkChannelRel(Table<O> child, ForeignKey<O, AkChannelRelRecord> key) {
        super(child, key, AK_CHANNEL_REL);
    }

    @Override
    public Schema getSchema() {
        return DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.AK_CHANNEL_REL_IDX_CHANNEL_CODE_AK_CODE);
    }

    @Override
    public UniqueKey<AkChannelRelRecord> getPrimaryKey() {
        return Keys.KEY_AK_CHANNEL_REL_PRIMARY;
    }

    @Override
    public List<UniqueKey<AkChannelRelRecord>> getKeys() {
        return Arrays.<UniqueKey<AkChannelRelRecord>>asList(Keys.KEY_AK_CHANNEL_REL_PRIMARY, Keys.KEY_AK_CHANNEL_REL_UNIQ_AK_CHANNEL_CODE);
    }

    @Override
    public AkChannelRel as(String alias) {
        return new AkChannelRel(DSL.name(alias), this);
    }

    @Override
    public AkChannelRel as(Name alias) {
        return new AkChannelRel(alias, this);
    }

    @Override
    public AkChannelRel rename(String name) {
        return new AkChannelRel(DSL.name(name), null);
    }

    @Override
    public AkChannelRel rename(Name name) {
        return new AkChannelRel(name, null);
    }

    @Override
    public Row9<Long, String, String, Long, String, Long, String, LocalDateTime, LocalDateTime> fieldsRow() {
        return (Row9) super.fieldsRow();
    }
}
