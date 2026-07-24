package com.ke.bella.openapi.script;

import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class LuaScriptExecutor {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private LuaScriptManager luaScriptManager;

    public Object execute(String fileName, ScriptType scriptType, List<Object> keys, List<Object> args) throws IOException {
        String scriptName = scriptType.getScriptName(fileName);
        String defaultName = scriptType.getScriptName("/default");
        String sha = luaScriptManager.getScriptSha(scriptName, defaultName);
        if(StringUtils.isBlank(sha)) {
            return null;
        }
        RScript rScript = redissonClient.getScript();
        return rScript.evalSha(RScript.Mode.READ_WRITE, sha, RScript.ReturnType.VALUE, keys, args.toArray());
    }
}
