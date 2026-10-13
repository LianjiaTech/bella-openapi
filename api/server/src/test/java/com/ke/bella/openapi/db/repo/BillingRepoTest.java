package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.billing.BillingOps;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.ke.bella.openapi.Tables.HIVE2MYSQL_BILLING_DAILY;
import static com.ke.bella.openapi.billing.BillingOps.GRANULARITY_DAY;
import static org.assertj.core.api.Assertions.assertThat;

public class BillingRepoTest {

    @Test
    public void pageRecordsFiltersOutNonPositiveAmountFromDataCountAndTotalAmount() {
        DSLContext resultDsl = DSL.using(SQLDialect.MYSQL);
        List<String> sqls = new ArrayList<>();
        List<Object[]> bindings = new ArrayList<>();
        MockConnection connection = new MockConnection(ctx -> {
            sqls.add(ctx.sql());
            bindings.add(ctx.bindings());
            String sql = normalizeSql(ctx.sql());
            if(sql.startsWith("select count(*)")) {
                return new MockResult[] { new MockResult(1, singleValueResult(resultDsl, "count", Integer.class, 1)) };
            }
            if(sql.contains("sum(")) {
                return new MockResult[] { new MockResult(1, singleValueResult(resultDsl, "amount", BigDecimal.class, new BigDecimal("0.1750"))) };
            }
            return new MockResult[] { new MockResult(1, billingRecordResult(resultDsl)) };
        });
        BillingRepo repo = new BillingRepo();
        ReflectionTestUtils.setField(repo, "db", DSL.using(connection, SQLDialect.MYSQL));

        BillingOps.BillingRecordPage page = repo.pageRecords(condition(), Collections.singleton("ak-positive"));

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getTotalAmount()).isEqualByComparingTo("0.0018");
        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0).getAmount()).isEqualByComparingTo("0.0018");
        assertThat(sqls).hasSize(3);
        assertThat(sqls).allSatisfy(sql -> assertThat(normalizeSql(sql)).contains("`amount` > ?"));
        assertThat(bindings).allSatisfy(binding -> assertThat(binding).contains(BigDecimal.ZERO));
    }

    private BillingOps.BillingRecordCondition condition() {
        BillingOps.BillingRecordCondition condition = new BillingOps.BillingRecordCondition();
        condition.setGranularity(GRANULARITY_DAY);
        condition.setAkCodes(Collections.singleton("ak-positive"));
        condition.setStartPt("20260701000000");
        condition.setEndPt("20260713235959");
        condition.setPage(1);
        condition.setSize(10);
        return condition;
    }

    private <T> Result<?> singleValueResult(DSLContext dsl, String name, Class<T> type, T value) {
        Field<T> field = DSL.field(name, type);
        Result result = dsl.newResult(field);
        Record record = dsl.newRecord(field);
        record.set(field, value);
        result.add(record);
        return result;
    }

    private Result<?> billingRecordResult(DSLContext dsl) {
        Result result = dsl.newResult(HIVE2MYSQL_BILLING_DAILY.PT, HIVE2MYSQL_BILLING_DAILY.ENDPOINT,
                HIVE2MYSQL_BILLING_DAILY.MODEL, HIVE2MYSQL_BILLING_DAILY.ACCOUNT_TYPE,
                HIVE2MYSQL_BILLING_DAILY.ACCOUNT_CODE, HIVE2MYSQL_BILLING_DAILY.AK_CODE,
                HIVE2MYSQL_BILLING_DAILY.AMOUNT);
        Record record = dsl.newRecord(HIVE2MYSQL_BILLING_DAILY.PT, HIVE2MYSQL_BILLING_DAILY.ENDPOINT,
                HIVE2MYSQL_BILLING_DAILY.MODEL, HIVE2MYSQL_BILLING_DAILY.ACCOUNT_TYPE,
                HIVE2MYSQL_BILLING_DAILY.ACCOUNT_CODE, HIVE2MYSQL_BILLING_DAILY.AK_CODE,
                HIVE2MYSQL_BILLING_DAILY.AMOUNT);
        record.set(HIVE2MYSQL_BILLING_DAILY.PT, "20260701000000");
        record.set(HIVE2MYSQL_BILLING_DAILY.ENDPOINT, "chat");
        record.set(HIVE2MYSQL_BILLING_DAILY.MODEL, "gpt-4o");
        record.set(HIVE2MYSQL_BILLING_DAILY.ACCOUNT_TYPE, "user");
        record.set(HIVE2MYSQL_BILLING_DAILY.ACCOUNT_CODE, "account-1");
        record.set(HIVE2MYSQL_BILLING_DAILY.AK_CODE, "ak-positive");
        record.set(HIVE2MYSQL_BILLING_DAILY.AMOUNT, new BigDecimal("0.1750"));
        result.add(record);
        return result;
    }

    private String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
