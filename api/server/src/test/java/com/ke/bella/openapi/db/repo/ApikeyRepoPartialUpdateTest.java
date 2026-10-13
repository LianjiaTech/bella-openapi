package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.apikey.SubApikeyUpdateOp;
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

public class ApikeyRepoPartialUpdateTest {

    @Test
    public void updateSubApikeyOnlyWritesProvidedFields() {
        List<String> sqls = new ArrayList<>();
        MockConnection connection = new MockConnection(ctx -> {
            sqls.add(ctx.sql());
            return new MockResult[] { new MockResult(1, null) };
        });
        ApikeyRepo repo = new ApikeyRepo();
        ReflectionTestUtils.setField(repo, "db", DSL.using(connection, SQLDialect.MYSQL));
        SubApikeyUpdateOp op = new SubApikeyUpdateOp();
        op.setCode("ak-child");
        op.setName("new-name");

        repo.updateSubApikeyFields(op);

        assertThat(sqls).hasSize(1);
        String sql = normalizeSql(sqls.get(0));
        String setClause = sql.substring(sql.indexOf(" set ") + 5, sql.indexOf(" where "));
        assertThat(setClause).contains("`name` = ?");
        assertThat(setClause).doesNotContain("`code` = ?");
        assertThat(setClause).doesNotContain("`out_entity_code` = ?");
        assertThat(setClause).doesNotContain("`safety_level` = ?");
        assertThat(setClause).doesNotContain("`month_quota` = ?");
        assertThat(setClause).doesNotContain("`role_code` = ?");
        assertThat(setClause).doesNotContain("`remark` = ?");
    }

    @Test
    public void updateSubApikeyDoesNotExecuteSqlWhenNoBasicFieldIsProvided() {
        List<String> sqls = new ArrayList<>();
        MockConnection connection = new MockConnection(ctx -> {
            sqls.add(ctx.sql());
            return new MockResult[] { new MockResult(1, null) };
        });
        ApikeyRepo repo = new ApikeyRepo();
        ReflectionTestUtils.setField(repo, "db", DSL.using(connection, SQLDialect.MYSQL));
        SubApikeyUpdateOp op = new SubApikeyUpdateOp();
        op.setCode("ak-child");

        repo.updateSubApikeyFields(op);

        assertThat(sqls).isEmpty();
    }

    @Test
    public void updateSubApikeyCanClearRemarkWithEmptyString() {
        List<String> sqls = new ArrayList<>();
        MockConnection connection = new MockConnection(ctx -> {
            sqls.add(ctx.sql());
            return new MockResult[] { new MockResult(1, null) };
        });
        ApikeyRepo repo = new ApikeyRepo();
        ReflectionTestUtils.setField(repo, "db", DSL.using(connection, SQLDialect.MYSQL));
        SubApikeyUpdateOp op = new SubApikeyUpdateOp();
        op.setCode("ak-child");
        op.setRemark("");

        repo.updateSubApikeyFields(op);

        assertThat(sqls).hasSize(1);
        assertThat(normalizeSql(sqls.get(0))).contains("`remark` = ?");
    }

    private String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
