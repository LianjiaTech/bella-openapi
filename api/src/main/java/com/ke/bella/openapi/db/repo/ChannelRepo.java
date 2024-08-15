package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.protocol.Condition;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.records.ChannelRecord;
import org.apache.commons.lang3.StringUtils;
import org.jooq.SelectSeekStep1;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.ke.bella.openapi.tables.Channel.CHANNEL;

/**
 * Author: Stan Sai Date: 2024/8/1 20:51 description:
 */
@Component
public class ChannelRepo extends StatusRepo<ChannelDB, ChannelRecord, String> implements AutogenCodeRepo<ChannelRecord> {

    public List<ChannelDB> list(Condition.ChannelCondition op) {
        return constructSql(op).fetchInto(ChannelDB.class);
    }

    public Page<ChannelDB> page(Condition.ChannelCondition op) {
        return queryPage(db, constructSql(op), op.getPageNum(), op.getPageSize(), ChannelDB.class);
    }

    private SelectSeekStep1<ChannelRecord, Long> constructSql(Condition.ChannelCondition op) {
        return db.selectFrom(CHANNEL)
                .where(StringUtils.isEmpty(op.getEntityType()) ? DSL.noCondition() : CHANNEL.ENTITY_TYPE.eq(op.getEntityType()))
                .and(StringUtils.isEmpty(op.getEntityType()) ? DSL.noCondition() : CHANNEL.ENTITY_CODE.eq(op.getEntityCode()))
                .and(StringUtils.isEmpty(op.getSupplier()) ? DSL.noCondition() : CHANNEL.SUPPLIER.eq(op.getSupplier()))
                .and(StringUtils.isEmpty(op.getProtocol()) ? DSL.noCondition() : CHANNEL.PROTOCOL.eq(op.getProtocol()))
                .and(StringUtils.isEmpty(op.getPriority()) ? DSL.noCondition() : CHANNEL.PRIORITY.eq(op.getPriority()))
                .and(StringUtils.isEmpty(op.getDataDestination()) ? DSL.noCondition() : CHANNEL.DATA_DESTINATION.eq(op.getDataDestination()))
                .and(StringUtils.isEmpty(op.getStatus()) ? DSL.noCondition() : CHANNEL.STATUS.eq(op.getStatus()))
                .orderBy(CHANNEL.ID.desc());
    }

    @Override
    public TableImpl<ChannelRecord> table() {
        return CHANNEL;
    }

    @Override
    protected TableField<ChannelRecord, String> uniqueKey() {
        return CHANNEL.CHANNEL_CODE;
    }

    @Override
    protected TableField<ChannelRecord, String> statusFiled() {
        return CHANNEL.STATUS;
    }

    @Override
    public TableField<ChannelRecord, String> autoCode() {
        return CHANNEL.CHANNEL_CODE;
    }

    @Override
    public String prefix() {
        return "ch_";
    }
}
