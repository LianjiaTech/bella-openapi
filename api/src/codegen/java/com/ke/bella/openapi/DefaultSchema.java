/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi;


import com.ke.bella.openapi.tables.OpenapiCategory;
import com.ke.bella.openapi.tables.OpenapiChannel;
import com.ke.bella.openapi.tables.OpenapiEndpoint;
import com.ke.bella.openapi.tables.OpenapiEndpointCategoryRelation;
import com.ke.bella.openapi.tables.OpenapiModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.12"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DefaultSchema extends SchemaImpl {

    private static final long serialVersionUID = 139288283;

    /**
     * The reference instance of <code></code>
     */
    public static final DefaultSchema DEFAULT_SCHEMA = new DefaultSchema();

    /**
     * openapi类目
     */
    public final OpenapiCategory OPENAPI_CATEGORY = com.ke.bella.openapi.tables.OpenapiCategory.OPENAPI_CATEGORY;

    /**
     * openapi模型
     */
    public final OpenapiChannel OPENAPI_CHANNEL = com.ke.bella.openapi.tables.OpenapiChannel.OPENAPI_CHANNEL;

    /**
     * openapi能力点
     */
    public final OpenapiEndpoint OPENAPI_ENDPOINT = com.ke.bella.openapi.tables.OpenapiEndpoint.OPENAPI_ENDPOINT;

    /**
     * openapi能力点类目
     */
    public final OpenapiEndpointCategoryRelation OPENAPI_ENDPOINT_CATEGORY_RELATION = com.ke.bella.openapi.tables.OpenapiEndpointCategoryRelation.OPENAPI_ENDPOINT_CATEGORY_RELATION;

    /**
     * openapi模型
     */
    public final OpenapiModel OPENAPI_MODEL = com.ke.bella.openapi.tables.OpenapiModel.OPENAPI_MODEL;

    /**
     * No further instances allowed
     */
    private DefaultSchema() {
        super("", null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Catalog getCatalog() {
        return DefaultCatalog.DEFAULT_CATALOG;
    }

    @Override
    public final List<Table<?>> getTables() {
        List result = new ArrayList();
        result.addAll(getTables0());
        return result;
    }

    private final List<Table<?>> getTables0() {
        return Arrays.<Table<?>>asList(
            OpenapiCategory.OPENAPI_CATEGORY,
            OpenapiChannel.OPENAPI_CHANNEL,
            OpenapiEndpoint.OPENAPI_ENDPOINT,
            OpenapiEndpointCategoryRelation.OPENAPI_ENDPOINT_CATEGORY_RELATION,
            OpenapiModel.OPENAPI_MODEL);
    }
}
