package com.ke.bella.openapi.service;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.metadata.EndpointDetails;
import com.ke.bella.openapi.metadata.Model;
import com.ke.bella.openapi.metadata.ModelCapacity;
import com.ke.bella.openapi.metadata.PriceDetails;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EndpointServiceTest {

    private static final String ENDPOINT = "/v1/chat/completions";

    @InjectMocks
    private EndpointService endpointService;

    @Mock
    private ModelService modelService;

    @Mock
    private ChannelService channelService;

    @Test
    public void getEndpointDetailsFiltersUnavailableAliasAndReusesTerminalForPrice() {
        Model availableModel = model("available-model");
        Model unavailableAlias = model("unavailable-alias");
        PriceDetails priceDetails = new PriceDetails();
        when(modelService.listByConditionWithPermission(any(Condition.ModelCondition.class), eq(false)))
                .thenReturn(Arrays.asList(availableModel, unavailableAlias));
        when(modelService.fetchTerminalModelName("available-model")).thenReturn("terminal-model");
        when(modelService.fetchTerminalModelName("unavailable-alias"))
                .thenThrow(new BizParamCheckException("模型不可用"));
        when(channelService.getPriceInfo(eq(Collections.singletonList("terminal-model")), any()))
                .thenReturn(Collections.singletonMap("terminal-model", priceDetails));

        EndpointDetails details = endpointService.getEndpointDetails(
                Condition.EndpointDetailsCondition.builder().endpoint(ENDPOINT).build(), "identity");

        assertThat(details.getModels()).extracting(Model::getModelName).containsExactly("available-model");
        assertThat(details.getModels().get(0).getTerminalModel()).isEqualTo("terminal-model");
        assertThat(details.getModels().get(0).getPriceDetails()).isSameAs(priceDetails);
        verify(modelService, times(1)).fetchTerminalModelName("available-model");
        verify(modelService, times(1)).fetchTerminalModelName("unavailable-alias");
    }

    @Test
    public void enrichEndpointDetailsCapacityFiltersUnavailableCachedAlias() {
        Model availableModel = model("available-model");
        Model unavailableAlias = model("unavailable-alias");
        ModelCapacity capacity = ModelCapacity.builder().type(ModelCapacity.TYPE_LLM).rpm(100L).build();
        EndpointDetails cachedDetails = EndpointDetails.builder()
                .endpoint(ENDPOINT)
                .models(Arrays.asList(availableModel, unavailableAlias))
                .build();
        when(modelService.fetchTerminalModelName("available-model")).thenReturn("terminal-model");
        when(modelService.fetchTerminalModelName("unavailable-alias"))
                .thenThrow(new BizParamCheckException("模型不可用"));
        when(channelService.aggregatePublicCapacity("terminal-model", ModelCapacity.TYPE_LLM)).thenReturn(capacity);

        EndpointDetails details = endpointService.enrichEndpointDetailsCapacity(ENDPOINT, cachedDetails);

        assertThat(details.getModels()).extracting(Model::getModelName).containsExactly("available-model");
        assertThat(details.getModels().get(0).getTerminalModel()).isEqualTo("terminal-model");
        assertThat(details.getModels().get(0).getCapacity()).isSameAs(capacity);
        assertThat(cachedDetails.getModels()).hasSize(2);
        verify(channelService).aggregatePublicCapacity("terminal-model", ModelCapacity.TYPE_LLM);
    }

    private Model model(String modelName) {
        Model model = new Model();
        model.setModelName(modelName);
        return model;
    }
}
