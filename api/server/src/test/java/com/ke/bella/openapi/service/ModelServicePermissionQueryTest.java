package com.ke.bella.openapi.service;

import com.ke.bella.openapi.db.repo.ModelRepo;
import com.ke.bella.openapi.db.repo.Page;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.metadata.Model;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ModelServicePermissionQueryTest {

    @InjectMocks
    private ModelService service;

    @Mock
    private ApikeyService apikeyService;

    @Mock
    private ModelRepo modelRepo;

    @Test
    public void permissionAwareListUsesPermissionRepositoryQuery() {
        Condition.ModelCondition condition = new Condition.ModelCondition();
        ModelDB db = new ModelDB();
        db.setModelName("private-model");
        when(modelRepo.listWithPermission(condition)).thenReturn(Collections.singletonList(db));

        List<Model> models = service.listByConditionWithPermission(condition, true);

        assertThat(models).extracting(Model::getModelName).containsExactly("private-model");
        verify(apikeyService).fillPermissionCode(condition, true);
        verify(modelRepo).listWithPermission(condition);
        verify(modelRepo, never()).list(condition);
    }

    @Test
    public void internalListKeepsUnrestrictedRepositoryQuery() {
        Condition.ModelCondition condition = new Condition.ModelCondition();
        when(modelRepo.list(condition)).thenReturn(Collections.emptyList());

        service.listByCondition(condition);

        verify(modelRepo).list(condition);
        verify(modelRepo, never()).listWithPermission(condition);
    }

    @Test
    public void permissionAwarePageUsesPermissionRepositoryQuery() {
        Condition.ModelCondition condition = new Condition.ModelCondition();
        Page<ModelDB> expected = new Page<>();
        when(modelRepo.pageWithPermission(condition)).thenReturn(expected);

        Page<ModelDB> actual = service.pageByConditionWithPermission(condition, true);

        assertThat(actual).isSameAs(expected);
        verify(apikeyService).fillPermissionCode(condition, true);
        verify(modelRepo).pageWithPermission(condition);
        verify(modelRepo, never()).page(condition);
    }
}
