package com.ke.bella.openapi.db.repo;

import com.ke.bella.openapi.apikey.ApikeyOps;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.ke.bella.openapi.tables.Apikey.APIKEY;
import static org.assertj.core.api.Assertions.assertThat;

public class ApikeyRepoOwnerOrManagerQueryTest {

    @Test
    public void ownerOrManagerConditionExcludesConsoleAndKeepsOtherFilters() {
        List<String> sqls = new ArrayList<>();
        MockConnection connection = new MockConnection(ctx -> {
            sqls.add(ctx.sql());
            return new MockResult[] { new MockResult(0, DSL.using(SQLDialect.MYSQL).newResult(APIKEY.fields())) };
        });
        ApikeyRepo repo = new ApikeyRepo();
        ReflectionTestUtils.setField(repo, "db", DSL.using(connection, SQLDialect.MYSQL));
        ApikeyOps.ApikeyCondition condition = new ApikeyOps.ApikeyCondition();
        condition.setOwnerOrManagerCode("1001");
        condition.setOwnerType("person");
        condition.setSearchParam("demo");

        repo.listAccessKeys(condition);

        assertThat(sqls).hasSize(1);
        String sql = normalizeSql(sqls.get(0));
        assertThat(sql).contains("`owner_type` = ?");
        assertThat(sql).contains("`name` like ? or `service_id` like ?");
        assertThat(sql).contains("`owner_type` <> ? and (`owner_code` = ? or `manager_code` = ?)");
        assertThat(sql).contains("`parent_code` = ?");
    }

    @Test
    public void managerOnlyChildConditionDoesNotAddOwnerBranch() {
        List<String> sqls = new ArrayList<>();
        MockConnection connection = new MockConnection(ctx -> {
            sqls.add(ctx.sql());
            return new MockResult[] { new MockResult(0, DSL.using(SQLDialect.MYSQL).newResult(APIKEY.fields())) };
        });
        ApikeyRepo repo = new ApikeyRepo();
        ReflectionTestUtils.setField(repo, "db", DSL.using(connection, SQLDialect.MYSQL));
        ApikeyOps.ApikeyCondition condition = new ApikeyOps.ApikeyCondition();
        condition.setManagerCode("1001");
        condition.setOnlyChild(true);

        repo.listAccessKeys(condition);

        String sql = normalizeSql(sqls.get(0));
        assertThat(sql).contains("`manager_code` = ?");
        assertThat(sql).contains("`parent_code` <> ?");
        assertThat(sql).doesNotContain("`owner_type` <> ?");
    }

    private String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replaceAll("`[^`]+`\\.", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
