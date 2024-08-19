/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi;


import com.ke.bella.openapi.tables.Category;
import com.ke.bella.openapi.tables.Channel;
import com.ke.bella.openapi.tables.Endpoint;
import com.ke.bella.openapi.tables.EndpointCategoryRel;
import com.ke.bella.openapi.tables.Model;
import com.ke.bella.openapi.tables.ModelAuthorizerRel;
import com.ke.bella.openapi.tables.ModelEndpointRel;

import java.util.Arrays;
import java.util.List;

import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DefaultSchema extends SchemaImpl {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>DEFAULT_SCHEMA</code>
     */
    public static final DefaultSchema DEFAULT_SCHEMA = new DefaultSchema();

    /**
     * openapi类目
     */
    public final Category CATEGORY = Category.CATEGORY;

    /**
     * 通道
     */
    public final Channel CHANNEL = Channel.CHANNEL;

    /**
     * openapi能力点
     */
    public final Endpoint ENDPOINT = Endpoint.ENDPOINT;

    /**
     * openapi能力点类目
     */
    public final EndpointCategoryRel ENDPOINT_CATEGORY_REL = EndpointCategoryRel.ENDPOINT_CATEGORY_REL;

    /**
     * 模型
     */
    public final Model MODEL = Model.MODEL;

    /**
     * 模型授权信息
     */
    public final ModelAuthorizerRel MODEL_AUTHORIZER_REL = ModelAuthorizerRel.MODEL_AUTHORIZER_REL;

    /**
     * 模型能力点
     */
    public final ModelEndpointRel MODEL_ENDPOINT_REL = ModelEndpointRel.MODEL_ENDPOINT_REL;

    /**
     * No further instances allowed
     */
    private DefaultSchema() {
        super("", null);
    }


    @Override
    public Catalog getCatalog() {
        return DefaultCatalog.DEFAULT_CATALOG;
    }

    @Override
    public final List<Table<?>> getTables() {
        return Arrays.<Table<?>>asList(
            Category.CATEGORY,
            Channel.CHANNEL,
            Endpoint.ENDPOINT,
            EndpointCategoryRel.ENDPOINT_CATEGORY_REL,
            Model.MODEL,
            ModelAuthorizerRel.MODEL_AUTHORIZER_REL,
            ModelEndpointRel.MODEL_ENDPOINT_REL);
    }
}
