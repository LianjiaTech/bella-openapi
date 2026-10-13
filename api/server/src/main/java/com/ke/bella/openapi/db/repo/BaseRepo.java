package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.BellaContext;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.SelectLimitStep;
import org.jooq.UpdatableRecord;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collection;

/**
 * Author: Stan Sai Date: 2024/8/8 00:55 description:
 */
public interface BaseRepo {
    default void fillCreatorInfo(Object object) {
        Assert.isTrue(object instanceof Operator, "非法的操作类型");
        Operator op = (Operator) object;
        com.ke.bella.openapi.Operator oper = BellaContext.getOperatorIgnoreNull();
        if(oper != null) {
            if(oper.getUserId() != null) {
                op.setCuid(oper.getUserId());
            }

            if(StringUtils.isNotEmpty(oper.getUserName())) {
                op.setCuName(oper.getUserName());
            }
        }
        fillUpdatorInfo(op);
    }

    default void fillUpdatorInfo(Object object) {
        Assert.isTrue(object instanceof Operator, "非法的操作类型");
        Operator op = (Operator) object;
        com.ke.bella.openapi.Operator oper = BellaContext.getOperatorIgnoreNull();
        if(oper != null) {
            if(oper.getUserId() != null) {
                op.setMuid(oper.getUserId());
            }
            if(StringUtils.isNotEmpty(oper.getUserName())) {
                op.setMuName(oper.getUserName());
            }
        }
    }

    default int batchExecuteQuery(DSLContext db, Collection<Query> queries) {
        int[] rows = db.batch(queries).execute();
        int sum = Arrays.stream(rows).sum();
        if(sum < queries.size()) {
            throw new IllegalStateException("批处理失败");
        }
        return sum;
    }

    default int batchInsert(DSLContext db, Collection<? extends UpdatableRecord<?>> records) {
        int[] rows = db.batchInsert(records).execute();
        int sum = Arrays.stream(rows).sum();
        if(sum < records.size()) {
            throw new IllegalStateException("批处理失败");
        }
        return sum;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    default <T> Page<T> queryPage(DSLContext db, SelectLimitStep scs, int page, int pageSize, Class<?> type) {
        if(scs == null) {
            return Page.from(page, pageSize);
        }
        return Page.from(page, pageSize)
                .total(db.fetchCount(scs))
                .list(scs.limit((page - 1) * pageSize, pageSize)
                        .fetch()
                        .into(type));
    }
}
