package com.ke.bella.openapi.db.repo;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class ApikeyRepoPersonalParentCountTest {

    @Test
    public void countOnlyIncludesActivePersonalParentsForOwner() {
        DSLContext resultDsl = DSL.using(SQLDialect.MYSQL);
        List<String> sqls = new ArrayList<>();
        List<Object[]> bindings = new ArrayList<>();
        MockConnection connection = new MockConnection(ctx -> {
            sqls.add(ctx.sql());
            bindings.add(ctx.bindings());
            return new MockResult[] { new MockResult(1, singleValueResult(resultDsl, 1)) };
        });
        ApikeyRepo repo = new ApikeyRepo();
        ReflectionTestUtils.setField(repo, "db", DSL.using(connection, SQLDialect.MYSQL));

        int count = repo.countActivePersonalParents("person-1");

        assertThat(count).isEqualTo(1);
        assertThat(sqls).hasSize(1);
        String sql = normalizeSql(sqls.get(0));
        assertThat(sql).contains("`owner_type` = ?");
        assertThat(sql).contains("`owner_code` = ?");
        assertThat(sql).contains("`status` = ?");
        assertThat(sql).contains("`parent_code` = ?");
        assertThat(bindings.get(0)).containsExactly("person", "person-1", "active", "");
    }

    private Result<?> singleValueResult(DSLContext dsl, int value) {
        Field<Integer> field = DSL.field("count", Integer.class);
        Result result = dsl.newResult(field);
        Record record = dsl.newRecord(field);
        record.set(field, value);
        result.add(record);
        return result;
    }

    private String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replaceAll("`[^`]+`\\.", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
