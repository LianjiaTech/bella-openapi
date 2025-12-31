package com.ke.bella.openapi.db.repo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.MappedTable;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

import java.util.regex.Pattern;

import static com.ke.bella.openapi.Tables.VIDEO_JOB;

public class DSLContextHolder {
    private static final Cache<String, DSLContext> configurations = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build();

    public static synchronized DSLContext get(String key, final DSLContext db) {
        if(StringUtils.isEmpty(key)) {
            return db;
        }

        DSLContext ret = configurations.getIfPresent(key);
        if(ret == null) {
            ret = DSL.using(db.configuration().derive(newSettings(key)));
            configurations.put(key, ret);
        }

        return ret;
    }

    public static Settings newSettings(String key) {
        return new Settings().withRenderMapping(new RenderMapping()
                .withSchemata(
                        new MappedSchema()
                                .withInputExpression(Pattern.compile(".*"))
                                .withTables(
                                        new MappedTable()
                                                .withInput(VIDEO_JOB.getName())
                                                .withOutput(targetTableName(VIDEO_JOB.getName(), key)))));
    }

    public static String targetTableName(String originalName, String key) {
        if(StringUtils.isEmpty(key)) {
            return originalName;
        }
        return String.format("%s_%s", originalName, key);
    }
}
