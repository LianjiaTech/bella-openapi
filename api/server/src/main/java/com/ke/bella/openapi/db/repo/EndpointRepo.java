package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.tables.pojos.EndpointDB;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import com.ke.bella.openapi.tables.records.EndpointRecord;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.SelectSeekStep1;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.ke.bella.openapi.Tables.ENDPOINT;

/**
 * Author: Stan Sai Date: 2024/8/1 19:55 description:
 */
@Component
public class EndpointRepo extends StatusRepo<EndpointDB, EndpointRecord, String> implements AutogenCodeRepo<EndpointRecord> {

    public EndpointDB queryByEndpointCode(String endpointCode) {
        return db.selectFrom(ENDPOINT)
                .where(ENDPOINT.ENDPOINT_CODE.eq(endpointCode))
                .fetchOneInto(EndpointDB.class);
    }

    public List<EndpointDB> list(Condition.EndpointCondition op) {
        return constructSql(op).fetchInto(EndpointDB.class);
    }

    public <T> List<T> list(Condition.EndpointCondition op, Class<T> type) {
        return constructSql(op).fetchInto(type);
    }

    public Page<EndpointDB> page(Condition.EndpointCondition op) {
        return queryPage(db, constructSql(op), op.getPage(), op.getSize(), EndpointDB.class);
    }

    private SelectSeekStep1<EndpointRecord, Long> constructSql(Condition.EndpointCondition op) {
        return db.selectFrom(ENDPOINT)
                .where(StringUtils.isEmpty(op.getEndpoint()) ? DSL.noCondition() : ENDPOINT.ENDPOINT_.eq(op.getEndpoint()))
                .and(StringUtils.isEmpty(op.getEndpointCode()) ? DSL.noCondition() : ENDPOINT.ENDPOINT_CODE.eq(op.getEndpointCode()))
                .and(StringUtils.isEmpty(op.getEndpointName())
                        ? DSL.noCondition()
                        : ENDPOINT.ENDPOINT_NAME.like("%" + op.getEndpointName() + "%"))
                .and(CollectionUtils.isEmpty(op.getEndpoints()) ? DSL.noCondition() : ENDPOINT.ENDPOINT_.in(op.getEndpoints()))
                .and(StringUtils.isEmpty(op.getMaintainerCode()) ? DSL.noCondition() : ENDPOINT.MAINTAINER_CODE.eq(op.getMaintainerCode()))
                .and(StringUtils.isEmpty(op.getMaintainerName())
                        ? DSL.noCondition()
                        : ENDPOINT.MAINTAINER_NAME.like("%" + op.getMaintainerCode() + "%"))
                .and(StringUtils.isEmpty(op.getStatus()) ? DSL.noCondition() : ENDPOINT.STATUS.eq(op.getStatus()))
                .orderBy(ENDPOINT.ID);
    }

    @Override
    public TableImpl<EndpointRecord> table() {
        return ENDPOINT;
    }

    @Override
    protected TableField<EndpointRecord, String> uniqueKey() {
        return ENDPOINT.ENDPOINT_;
    }

    @Override
    protected TableField<EndpointRecord, String> statusFiled() {
        return ENDPOINT.STATUS;
    }

    @Override
    public TableField<EndpointRecord, String> autoCode() {
        return ENDPOINT.ENDPOINT_CODE;
    }

    @Override
    public String prefix() {
        return "ep-";
    }
}
