package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.billing.BillingOps;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import static com.ke.bella.openapi.Tables.HIVE2MYSQL_BILLING_DAILY;
import static com.ke.bella.openapi.Tables.HIVE2MYSQL_BILLING_MONTH;
import static com.ke.bella.openapi.billing.BillingOps.GRANULARITY_DAY;

@Component
public class BillingRepo {
    @Autowired
    private DSLContext db;

    private static final int AMOUNT_SCALE = 4;

    public BillingOps.BillingRecordPage pageRecords(BillingOps.BillingRecordCondition condition, Set<String> akCodes) {
        BillingFields fields = resolveFields(condition.getGranularity());
        Condition where = buildWhere(condition, akCodes, fields);
        int page = condition.getPage();
        int size = condition.getSize();

        Integer total = db.selectCount()
                .from(fields.table)
                .where(where)
                .fetchOne(0, Integer.class);
        BigDecimal totalAmount = queryTotalAmount(fields, where);
        List<BillingOps.BillingRecord> data = db.select(fields.pt, fields.endpoint, fields.model, fields.accountType,
                        fields.accountCode, fields.akCode, fields.amount)
                .from(fields.table)
                .where(where)
                .orderBy(fields.pt.desc(), fields.akCode.asc(), fields.endpoint.asc(), fields.model.asc(), fields.id.desc())
                .limit((page - 1) * size, size)
                .fetch(record -> BillingOps.BillingRecord.builder()
                        .pt(record.get(fields.pt))
                        .endpoint(record.get(fields.endpoint))
                        .model(record.get(fields.model))
                        .accountType(record.get(fields.accountType))
                        .accountCode(record.get(fields.accountCode))
                        .akCode(record.get(fields.akCode))
                        .amount(toYuan(record.get(fields.amount), AMOUNT_SCALE))
                        .build());

        return BillingOps.BillingRecordPage.builder()
                .page(page)
                .pageSize(size)
                .total(total == null ? 0 : total)
                .totalAmount(totalAmount)
                .data(data)
                .build();
    }

    private BigDecimal queryTotalAmount(BillingFields fields, Condition where) {
        BigDecimal cents = db.select(DSL.coalesce(DSL.sum(fields.amount.cast(BigDecimal.class)), BigDecimal.ZERO))
                .from(fields.table)
                .where(where)
                .fetchOne(0, BigDecimal.class);
        return toYuan(cents, AMOUNT_SCALE);
    }

    private Condition buildWhere(BillingOps.BillingRecordCondition condition, Set<String> akCodes, BillingFields fields) {
        Condition where = fields.akCode.in(akCodes)
                .and(fields.pt.between(condition.getStartPt(), condition.getEndPt()))
                .and(fields.amount.gt(BigDecimal.ZERO));
        if(condition.getEndpoints() != null && !condition.getEndpoints().isEmpty()) {
            where = where.and(fields.endpoint.in(condition.getEndpoints()));
        }
        if(StringUtils.isNotEmpty(condition.getModel())) {
            where = where.and(fields.model.eq(condition.getModel()));
        }
        return where;
    }

    private BillingFields resolveFields(String granularity) {
        if(GRANULARITY_DAY.equals(granularity)) {
            return new BillingFields(HIVE2MYSQL_BILLING_DAILY, HIVE2MYSQL_BILLING_DAILY.ID, HIVE2MYSQL_BILLING_DAILY.PT,
                    HIVE2MYSQL_BILLING_DAILY.ENDPOINT, HIVE2MYSQL_BILLING_DAILY.MODEL, HIVE2MYSQL_BILLING_DAILY.ACCOUNT_TYPE,
                    HIVE2MYSQL_BILLING_DAILY.ACCOUNT_CODE, HIVE2MYSQL_BILLING_DAILY.AK_CODE, HIVE2MYSQL_BILLING_DAILY.AMOUNT);
        }
        return new BillingFields(HIVE2MYSQL_BILLING_MONTH, HIVE2MYSQL_BILLING_MONTH.ID, HIVE2MYSQL_BILLING_MONTH.PT,
                HIVE2MYSQL_BILLING_MONTH.ENDPOINT, HIVE2MYSQL_BILLING_MONTH.MODEL, HIVE2MYSQL_BILLING_MONTH.ACCOUNT_TYPE,
                HIVE2MYSQL_BILLING_MONTH.ACCOUNT_CODE, HIVE2MYSQL_BILLING_MONTH.AK_CODE, HIVE2MYSQL_BILLING_MONTH.AMOUNT);
    }

    private BigDecimal toYuan(Number cents, int scale) {
        return cents == null ? BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
                : new BigDecimal(cents.toString()).divide(BigDecimal.valueOf(100), scale, RoundingMode.HALF_UP);
    }

    private static class BillingFields {
        private final Table<?> table;
        private final Field<Long> id;
        private final Field<String> pt;
        private final Field<String> endpoint;
        private final Field<String> model;
        private final Field<String> accountType;
        private final Field<String> accountCode;
        private final Field<String> akCode;
        private final Field<BigDecimal> amount;

        private BillingFields(Table<?> table, Field<Long> id, Field<String> pt, Field<String> endpoint, Field<String> model,
                Field<String> accountType, Field<String> accountCode, Field<String> akCode, Field<BigDecimal> amount) {
            this.table = table;
            this.id = id;
            this.pt = pt;
            this.endpoint = endpoint;
            this.model = model;
            this.accountType = accountType;
            this.accountCode = accountCode;
            this.akCode = akCode;
            this.amount = amount;
        }
    }
}
