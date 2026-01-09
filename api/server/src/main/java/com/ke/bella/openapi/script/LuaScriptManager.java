package com.ke.bella.openapi.script;

import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.alicp.jetcache.template.QuickConfig;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class LuaScriptManager {
    private static final String luaDir = "classpath:/lua/";
    private static final String luaScriptsKey = "luaScripts:";
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private ResourceLoader resourceLoader;

    @PostConstruct
    public void init() throws IOException {
        QuickConfig config = QuickConfig.newBuilder(luaScriptsKey)
                .expire(Duration.ofDays(3650))
                .localExpire(Duration.ofMinutes(5))
                .cacheNullValue(true)
                .cacheType(CacheType.BOTH)
                .localLimit(100)
                .penetrationProtect(false)
                .build();
        cacheManager.getOrCreateCache(config);
    }

    private String doLoad(String fileName, String defaultName) throws IOException {
        Resource resource = resourceLoader.getResource(luaDir + fileName);
        if(!resource.exists() && StringUtils.isNotEmpty(defaultName)) {
            resource = resourceLoader.getResource(luaDir + defaultName);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            String scriptContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            return redissonClient.getScript().scriptLoad(scriptContent);
        }
    }

    @Cached(name = luaScriptsKey, key = "#scriptName")
    public String getScriptSha(String scriptName, String defaultName) throws IOException {
        return doLoad(scriptName + ".lua", defaultName + ".lua");
    }

    public String reloadScript(String scriptName, String defaultName) throws IOException {
        String sha = doLoad(scriptName + ".lua", defaultName + ".lua");
        cacheManager.getCache(luaScriptsKey).put(scriptName, sha);
        return sha;
    }
}
