package com.ke.bella.openapi.db.repo;

import org.jooq.Condition;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnit4.class)
public class ModelRepoPermissionConditionTest {

    @Test
    public void unscopedRepositoryQueriesCanStillOptOutOfPermissionFiltering() {
        Condition condition = ModelRepo.permissionCondition(null, Collections.emptySet(), null, false);

        assertThat(render(condition)).isEqualTo("true");
        assertThat(render(condition)).doesNotContain("visibility");
    }

    @Test
    public void emptyPermissionScopeFallsBackToPublicModelsOnly() {
        String sql = render(ModelRepo.permissionCondition(null, Collections.emptySet(), null));

        assertThat(sql).contains("visibility", "public");
        assertThat(sql).doesNotContain("model_authorizer_rel");
        assertThat(sql).doesNotContain("1 = 1");
    }

    @Test
    public void permissionScopeMatchesPersonOrgAndProjectAuthorizers() {
        LinkedHashSet<String> orgCodes = new LinkedHashSet<>();
        orgCodes.add("org-a");
        orgCodes.add("org-b");

        String sql = render(ModelRepo.permissionCondition("person-a", orgCodes, "project-a"));

        assertThat(sql).contains("visibility", "public", "exists", "model_authorizer_rel");
        assertThat(sql).contains("authorizer_type", "'person'", "authorizer_code", "'person-a'");
        assertThat(sql).contains("authorizer_type", "'org'", "org-a", "org-b");
        assertThat(sql).contains("authorizer_type", "'project'", "authorizer_code", "'project-a'");
    }

    @Test
    public void projectScopeDoesNotGrantPersonOrOrgAuthorizers() {
        String sql = render(ModelRepo.permissionCondition(null, Collections.emptySet(), "project-a"));

        assertThat(sql).contains("visibility", "public", "authorizer_type", "'project'", "authorizer_code", "'project-a'");
        assertThat(sql).doesNotContain("'person'");
        assertThat(sql).doesNotContain("'org'");
    }

    private String render(Condition condition) {
        return DSL.using(SQLDialect.MYSQL)
                .renderInlined(condition)
                .toLowerCase();
    }
}
