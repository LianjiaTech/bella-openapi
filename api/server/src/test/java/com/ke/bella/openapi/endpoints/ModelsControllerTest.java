package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.protocol.model.ModelListResponse;
import com.ke.bella.openapi.service.ModelService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ModelsControllerTest {

    @InjectMocks
    private ModelsController controller;

    @Mock
    private ModelService modelService;

    @Test
    public void listModelsUsesActivePermissionAwareQuery() {
        when(modelService.listByConditionWithPermission(any(Condition.ModelCondition.class), eq(true)))
                .thenReturn(Collections.emptyList());

        ModelListResponse response = controller.listModels();

        ArgumentCaptor<Condition.ModelCondition> captor = ArgumentCaptor.forClass(Condition.ModelCondition.class);
        verify(modelService).listByConditionWithPermission(captor.capture(), eq(true));
        assertThat(captor.getValue().getStatus()).isEqualTo(EntityConstants.ACTIVE);
        assertThat(response.getData()).isEmpty();
    }
}
