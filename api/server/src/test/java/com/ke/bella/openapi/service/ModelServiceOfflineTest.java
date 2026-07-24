package com.ke.bella.openapi.service;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.db.repo.ModelRepo;
import com.ke.bella.openapi.metadata.MetaDataOps;
import com.ke.bella.openapi.metadata.OfflinePlan;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ke.bella.openapi.common.EntityConstants.ACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.INACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.MODEL;
import static com.ke.bella.openapi.common.EntityConstants.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ModelServiceOfflineTest {

    @InjectMocks
    private ModelService service;

    @Mock
    private ModelRepo modelRepo;

    @Mock
    private ChannelService channelService;

    @Mock
    private AkPermissionChecker akPermissionChecker;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache<Object, Object> cache;

    @Test
    public void offlineModelRequiresAdminPermission() {
        when(akPermissionChecker.hasAdminPermission()).thenReturn(false);

        assertThatThrownBy(() -> service.offlineModel(offlineOp("gpt-4o", "gpt-4o")))
                .isInstanceOf(BellaException.AuthorizationException.class)
                .hasMessageContaining("没有模型下线权限");

        verify(modelRepo, never()).updateStatusAndVisibility(anyList(), eq(INACTIVE), eq(PRIVATE));
        verify(channelService, never()).changeStatusByEntities(eq(MODEL), anyList(), eq(false));
    }

    @Test
    public void offlineModelInactivatesAffectedModelsAndModelChannels() {
        List<ModelDB> models = Arrays.asList(
                model("gpt-4o", null, false),
                model("alias-direct", "gpt-4o", false),
                model("alias-indirect", "alias-direct", false));
        List<String> expectedModels = Arrays.asList("gpt-4o", "alias-direct", "alias-indirect");
        when(akPermissionChecker.hasAdminPermission()).thenReturn(true);
        when(modelRepo.listAll()).thenReturn(models);
        when(channelService.changeStatusByEntities(MODEL, expectedModels, false)).thenReturn(3);
        when(cacheManager.getCache("model:map:")).thenReturn(cache);
        when(cache.tryLockAndRun(eq("lock"), eq(10L), eq(TimeUnit.SECONDS), any(Runnable.class))).thenReturn(true);

        service.offlineModel(offlineOp("gpt-4o", expectedModels.toArray(new String[0])));

        InOrder inOrder = inOrder(channelService, modelRepo);
        inOrder.verify(channelService).changeStatusByEntities(MODEL, expectedModels, false);
        inOrder.verify(modelRepo).updateStatusAndVisibility(expectedModels, INACTIVE, PRIVATE);
    }

    @Test
    public void resolveOfflinePlanIncludesActiveModelsAndEdges() {
        when(modelRepo.listAll()).thenReturn(Arrays.asList(
                model("C", null, false),
                model("B", "C", false),
                model("D", "C", false),
                model("A", "B", false),
                model("X", "B", false, INACTIVE),
                model("Y", "X", false)));

        OfflinePlan plan = service.resolveOfflinePlan("C");

        assertThat(plan.getRoot()).isEqualTo("C");
        assertThat(plan.getAffectedModels()).containsExactly("C", "B", "D", "A");
        assertThat(plan.getEdges()).extracting(OfflinePlan.Edge::getFrom).containsExactly("B", "D", "A");
        assertThat(plan.getEdges()).extracting(OfflinePlan.Edge::getTo).containsExactly("C", "C", "B");
    }

    @Test
    public void resolveOfflinePlanRejectsInactiveTarget() {
        when(modelRepo.listAll()).thenReturn(Arrays.asList(model("gpt-4o", null, false, INACTIVE)));

        assertThatThrownBy(() -> service.resolveOfflinePlan("gpt-4o"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型不可用或不存在");
    }

    @Test
    public void offlineModelRejectsChangedOfflinePlan() {
        when(akPermissionChecker.hasAdminPermission()).thenReturn(true);
        when(modelRepo.listAll()).thenReturn(Arrays.asList(
                model("gpt-4o", null, false),
                model("alias-direct", "gpt-4o", false)));

        assertThatThrownBy(() -> service.offlineModel(offlineOp("gpt-4o", "gpt-4o")))
                .isInstanceOf(BizParamCheckException.class)
                .hasMessageContaining("软链关系已变化，请重新确认后下线");

        verify(modelRepo, never()).updateStatusAndVisibility(anyList(), eq(INACTIVE), eq(PRIVATE));
        verify(channelService, never()).changeStatusByEntities(eq(MODEL), anyList(), eq(false));
    }

    private MetaDataOps.ModelOfflineOp offlineOp(String modelName, String... expectedModels) {
        MetaDataOps.ModelOfflineOp op = new MetaDataOps.ModelOfflineOp();
        op.setModelName(modelName);
        op.setExpectedModels(Arrays.asList(expectedModels));
        return op;
    }

    private ModelDB model(String modelName, String linkedTo, boolean deleted) {
        return model(modelName, linkedTo, deleted, ACTIVE);
    }

    private ModelDB model(String modelName, String linkedTo, boolean deleted, String status) {
        ModelDB model = new ModelDB();
        model.setModelName(modelName);
        model.setLinkedTo(linkedTo);
        model.setDeleted(deleted ? (byte) 1 : (byte) 0);
        model.setStatus(status);
        return model;
    }
}
