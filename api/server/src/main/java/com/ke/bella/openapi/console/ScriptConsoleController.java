package com.ke.bella.openapi.console;

import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.script.LuaScriptManager;
import com.ke.bella.openapi.script.ScriptInfo;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@BellaAPI
@RestController
@RequestMapping("/console/script")
public class ScriptConsoleController {
    @Autowired
    private LuaScriptManager luaScriptManager;
    @Autowired
    private ModelService modelService;

    @PostMapping("/lua/reload")
    public String reloadLuaScript(@RequestBody ScriptInfo script) throws IOException {
        Assert.isTrue(StringUtils.isNotBlank(script.getScriptName()), "scriptName cannot be empty");
        return luaScriptManager.reloadScript(script.getScriptName(), script.getDefaultName());
    }

    @GetMapping("/check/model/map")
    public Map<String, ModelDB> listMap() {
        return modelService.queryWithCache("all");
    }

    @PostMapping("/model/map/refresh")
    public Boolean refresh() {
        modelService.refreshModelMap();
        return true;
    }

}
