package com.ke.bella.openapi.db.repo;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.ke.bella.openapi.tables.Channel.CHANNEL;
import static org.assertj.core.api.Assertions.assertThat;

public class ChannelRepoTest {

    @Test
    public void queryPriceInfoReturnsPublicPriceWhenPublicAndPrivateChannelsCoexist() {
        QueryCapture capture = executeQuery(singlePriceResult("shared-model", "public-price"),
                Collections.singletonList("shared-model"));

        assertThat(capture.prices).containsOnlyKeys("shared-model");
        assertThat(capture.prices.get("shared-model")).isEqualTo("public-price");
        assertPublicActiveFilter(capture);
    }

    @Test
    public void queryPriceInfoDoesNotReturnPriceWhenOnlyPrivateChannelExists() {
        QueryCapture capture = executeQuery(emptyPriceResult(), Collections.singletonList("private-model"));

        assertThat(capture.prices).isEmpty();
        assertPublicActiveFilter(capture);
    }

    private QueryCapture executeQuery(Result<Record2<String, String>> result, List<String> entityCodes) {
        List<String> sqls = new ArrayList<>();
        List<Object> bindings = new ArrayList<>();
        MockConnection connection = new MockConnection(ctx -> {
            sqls.add(ctx.sql());
            bindings.addAll(Arrays.asList(ctx.bindings()));
            return new MockResult[] { new MockResult(result.size(), result) };
        });
        ChannelRepo repo = new ChannelRepo();
        ReflectionTestUtils.setField(repo, "db", DSL.using(connection, SQLDialect.MYSQL));

        Map<String, String> prices = repo.queryPriceInfo(entityCodes);

        assertThat(sqls).hasSize(1);
        return new QueryCapture(sqls.get(0), bindings, prices);
    }

    private void assertPublicActiveFilter(QueryCapture capture) {
        String sql = capture.sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        assertThat(sql).contains("`status` = ?", "`visibility` = ?");
        assertThat(capture.bindings).contains("active", "public");
    }

    private Result<Record2<String, String>> singlePriceResult(String entityCode, String priceInfo) {
        DSLContext dsl = DSL.using(SQLDialect.MYSQL);
        Field<String> priceField = DSL.field("ANY_VALUE({0})", String.class, CHANNEL.PRICE_INFO);
        Result<Record2<String, String>> result = dsl.newResult(CHANNEL.ENTITY_CODE, priceField);
        Record2<String, String> record = dsl.newRecord(CHANNEL.ENTITY_CODE, priceField);
        record.set(CHANNEL.ENTITY_CODE, entityCode);
        record.set(priceField, priceInfo);
        result.add(record);
        return result;
    }

    private Result<Record2<String, String>> emptyPriceResult() {
        DSLContext dsl = DSL.using(SQLDialect.MYSQL);
        return dsl.newResult(CHANNEL.ENTITY_CODE, DSL.field("ANY_VALUE({0})", String.class, CHANNEL.PRICE_INFO));
    }

    private static class QueryCapture {
        private final String sql;
        private final List<Object> bindings;
        private final Map<String, String> prices;

        private QueryCapture(String sql, List<Object> bindings, Map<String, String> prices) {
            this.sql = sql;
            this.bindings = bindings;
            this.prices = prices;
        }
    }
}
