package com.ke.bella.openapi.service;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;

import static com.ke.bella.openapi.common.EntityConstants.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(JUnit4.class)
public class ModelServiceTerminalModelTest {

    private final ModelService modelService = new ModelService();

    @Test
    public void resolveTerminalModelNameReturnsTerminalForActivePath() {
        Map<String, ModelDB> map = new HashMap<>();
        map.put("alias", model("alias", "terminal", ACTIVE));
        map.put("terminal", model("terminal", null, ACTIVE));

        assertThat(modelService.resolveTerminalModelName("alias", map)).isEqualTo("terminal");
    }

    @Test
    public void resolveTerminalModelNameRejectsDeletedRequestedModel() {
        Map<String, ModelDB> map = new HashMap<>();
        map.put("deleted-model", model("deleted-model", null, ACTIVE, true));

        assertThatThrownBy(() -> modelService.resolveTerminalModelName("deleted-model", map))
                .isInstanceOf(BizParamCheckException.class)
                .hasMessage("模型不存在");
    }

    @Test
    public void resolveTerminalModelNameRejectsPathWithDeletedTerminal() {
        Map<String, ModelDB> map = new HashMap<>();
        map.put("alias", model("alias", "deleted-terminal", ACTIVE));
        map.put("deleted-terminal", model("deleted-terminal", null, ACTIVE, true));

        assertThatThrownBy(() -> modelService.resolveTerminalModelName("alias", map))
                .isInstanceOf(BizParamCheckException.class)
                .hasMessage("模型不存在");
    }

    @Test
    public void resolveTerminalModelNameRejectsInactiveRequestedModel() {
        Map<String, ModelDB> map = new HashMap<>();
        map.put("inactive-model", model("inactive-model", null, "inactive"));

        assertThatThrownBy(() -> modelService.resolveTerminalModelName("inactive-model", map))
                .isInstanceOf(BizParamCheckException.class)
                .hasMessage("模型不可用");
    }

    @Test
    public void resolveTerminalModelNameRejectsPathWithInactiveLinkedModel() {
        Map<String, ModelDB> map = new HashMap<>();
        map.put("alias", model("alias", "terminal", "inactive"));
        map.put("terminal", model("terminal", null, ACTIVE));

        assertThatThrownBy(() -> modelService.resolveTerminalModelName("alias", map))
                .isInstanceOf(BizParamCheckException.class)
                .hasMessage("模型不可用");
    }

    @Test
    public void resolveTerminalModelNameUsesSelfWhenRestoredModelClearsLinkedTo() {
        Map<String, ModelDB> map = new HashMap<>();
        map.put("restored", model("restored", "", ACTIVE));
        map.put("old-terminal", model("old-terminal", null, ACTIVE));

        assertThat(modelService.resolveTerminalModelName("restored", map)).isEqualTo("restored");
    }

    @Test
    public void resolveTerminalModelNameUsesSelfWhenDeletedTargetLinkIsCleared() {
        Map<String, ModelDB> map = new HashMap<>();
        map.put("alias", model("alias", "", ACTIVE));
        map.put("deleted-target", model("deleted-target", null, ACTIVE, true));

        assertThat(modelService.resolveTerminalModelName("alias", map)).isEqualTo("alias");
    }

    private ModelDB model(String modelName, String linkedTo, String status) {
        return model(modelName, linkedTo, status, false);
    }

    private ModelDB model(String modelName, String linkedTo, String status, boolean deleted) {
        ModelDB model = new ModelDB();
        model.setModelName(modelName);
        model.setLinkedTo(linkedTo);
        model.setStatus(status);
        model.setDeleted(deleted ? (byte) 1 : (byte) 0);
        return model;
    }
}
