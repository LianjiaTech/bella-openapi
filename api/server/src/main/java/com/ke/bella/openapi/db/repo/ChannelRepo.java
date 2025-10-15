package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.records.ChannelRecord;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SelectSeekStep1;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.ke.bella.openapi.tables.Channel.CHANNEL;

/**
 * Author: Stan Sai Date: 2024/8/1 20:51 description:
 */
@Component
public class ChannelRepo extends StatusRepo<ChannelDB, ChannelRecord, String> implements AutogenCodeRepo<ChannelRecord> {

    public Map<String, String> queryPriceInfo(List<String> entityCodes) {
        Result<Record2<String, String>> result = db
                .select(CHANNEL.ENTITY_CODE, DSL.field("ANY_VALUE({0})", String.class, CHANNEL.PRICE_INFO))
                .from(CHANNEL)
                .where(CHANNEL.ENTITY_CODE.in(entityCodes).and(CHANNEL.STATUS.eq(EntityConstants.ACTIVE)))
                .groupBy(CHANNEL.ENTITY_CODE)
                .fetch();
        return result.intoMap(CHANNEL.ENTITY_CODE, DSL.field("ANY_VALUE({0})", String.class, CHANNEL.PRICE_INFO));
    }

    public List<String> listSuppliers() {
        return db.selectDistinct(CHANNEL.SUPPLIER).from(CHANNEL).fetchInto(String.class);
    }
    public List<ChannelDB> list(Condition.ChannelCondition op) {
        return constructSql(op).fetchInto(ChannelDB.class);
    }

    public <H> List<H> list(Condition.ChannelCondition op, Class<H> type) {
        return constructSql(op).fetchInto(type);
    }

    public Page<ChannelDB> page(Condition.ChannelCondition op) {
        return queryPage(db, constructSql(op), op.getPage(), op.getSize(), ChannelDB.class);
    }

    private SelectSeekStep1<ChannelRecord, Long> constructSql(Condition.ChannelCondition op) {
        return db.selectFrom(CHANNEL)
                .where(StringUtils.isEmpty(op.getEntityType()) ? DSL.noCondition() : CHANNEL.ENTITY_TYPE.eq(op.getEntityType()))
                .and(StringUtils.isEmpty(op.getEntityCode()) ? DSL.noCondition() : CHANNEL.ENTITY_CODE.eq(op.getEntityCode()))
                .and(CollectionUtils.isEmpty(op.getEntityCodes()) ? DSL.noCondition() : CHANNEL.ENTITY_CODE.in(op.getEntityCodes()))
                .and(StringUtils.isEmpty(op.getSupplier()) ? DSL.noCondition() : CHANNEL.SUPPLIER.eq(op.getSupplier()))
                .and(StringUtils.isEmpty(op.getProtocol()) ? DSL.noCondition() : CHANNEL.PROTOCOL.eq(op.getProtocol()))
                .and(StringUtils.isEmpty(op.getPriority()) ? DSL.noCondition() : CHANNEL.PRIORITY.eq(op.getPriority()))
                .and(StringUtils.isEmpty(op.getDataDestination()) ? DSL.noCondition() : CHANNEL.DATA_DESTINATION.eq(op.getDataDestination()))
                .and(StringUtils.isEmpty(op.getStatus()) ? DSL.noCondition() : CHANNEL.STATUS.eq(op.getStatus()))
                .and(StringUtils.isEmpty(op.getVisibility()) ? DSL.noCondition() : CHANNEL.VISIBILITY.eq(op.getVisibility()))
                .and(StringUtils.isEmpty(op.getOwnerType()) ? DSL.noCondition() : CHANNEL.OWNER_TYPE.eq(op.getOwnerType()))
                .and(StringUtils.isEmpty(op.getOwnerCode()) ? DSL.noCondition() : CHANNEL.OWNER_CODE.eq(op.getOwnerCode()))
                .and(CollectionUtils.isEmpty(op.getQueueModes()) ? DSL.noCondition() : CHANNEL.QUEUE_MODE.in(op.getQueueModes()))
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
        return "ch-";
    }
}
