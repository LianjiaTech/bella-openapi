/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi;


import com.ke.bella.openapi.tables.Category;
import com.ke.bella.openapi.tables.Channel;
import com.ke.bella.openapi.tables.Endpoint;
import com.ke.bella.openapi.tables.EndpointCategoryRel;
import com.ke.bella.openapi.tables.Model;
import com.ke.bella.openapi.tables.ModelEndpointRel;

import javax.annotation.Generated;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.Internal;


/**
 * A class modelling indexes of tables of the <code></code> schema.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.11.12"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index CATEGORY_IDX_CATEGORY_NAME = Indexes0.CATEGORY_IDX_CATEGORY_NAME;
    public static final Index CATEGORY_IDX_PARENT_CODE = Indexes0.CATEGORY_IDX_PARENT_CODE;
    public static final Index CATEGORY_PRIMARY = Indexes0.CATEGORY_PRIMARY;
    public static final Index CATEGORY_UNIQ_IDX_UNI_CATEGORY_CODE = Indexes0.CATEGORY_UNIQ_IDX_UNI_CATEGORY_CODE;
    public static final Index CHANNEL_IDX_ENTITY_TYPE_CODE = Indexes0.CHANNEL_IDX_ENTITY_TYPE_CODE;
    public static final Index CHANNEL_IDX_PROTOCOL = Indexes0.CHANNEL_IDX_PROTOCOL;
    public static final Index CHANNEL_IDX_SUPPLIER = Indexes0.CHANNEL_IDX_SUPPLIER;
    public static final Index CHANNEL_PRIMARY = Indexes0.CHANNEL_PRIMARY;
    public static final Index CHANNEL_UNIQ_IDX_UNI_CHANNEL_CODE = Indexes0.CHANNEL_UNIQ_IDX_UNI_CHANNEL_CODE;
    public static final Index ENDPOINT_PRIMARY = Indexes0.ENDPOINT_PRIMARY;
    public static final Index ENDPOINT_UNIQ_IDX_UNI_ENDPOINT = Indexes0.ENDPOINT_UNIQ_IDX_UNI_ENDPOINT;
    public static final Index ENDPOINT_UNIQ_IDX_UNI_ENDPOINT_CODE = Indexes0.ENDPOINT_UNIQ_IDX_UNI_ENDPOINT_CODE;
    public static final Index ENDPOINT_UNIQ_IDX_UNI_ENDPOINT_NAME = Indexes0.ENDPOINT_UNIQ_IDX_UNI_ENDPOINT_NAME;
    public static final Index ENDPOINT_CATEGORY_REL_IDX_CATEGORY_CODE = Indexes0.ENDPOINT_CATEGORY_REL_IDX_CATEGORY_CODE;
    public static final Index ENDPOINT_CATEGORY_REL_IDX_SORT = Indexes0.ENDPOINT_CATEGORY_REL_IDX_SORT;
    public static final Index ENDPOINT_CATEGORY_REL_PRIMARY = Indexes0.ENDPOINT_CATEGORY_REL_PRIMARY;
    public static final Index ENDPOINT_CATEGORY_REL_UNIQ_IDX_UNI_ENDPOINT_CATEGORY_CODE = Indexes0.ENDPOINT_CATEGORY_REL_UNIQ_IDX_UNI_ENDPOINT_CATEGORY_CODE;
    public static final Index MODEL_PRIMARY = Indexes0.MODEL_PRIMARY;
    public static final Index MODEL_UNIQ_IDX_UNI_MODEL_NAME = Indexes0.MODEL_UNIQ_IDX_UNI_MODEL_NAME;
    public static final Index MODEL_ENDPOINT_REL_IDX_MODEL_NAME = Indexes0.MODEL_ENDPOINT_REL_IDX_MODEL_NAME;
    public static final Index MODEL_ENDPOINT_REL_PRIMARY = Indexes0.MODEL_ENDPOINT_REL_PRIMARY;
    public static final Index MODEL_ENDPOINT_REL_UNIQ_IDX_UNI_ENDPOINT_MODEL = Indexes0.MODEL_ENDPOINT_REL_UNIQ_IDX_UNI_ENDPOINT_MODEL;

    // -------------------------------------------------------------------------
    // [#1459] distribute members to avoid static initialisers > 64kb
    // -------------------------------------------------------------------------

    private static class Indexes0 {
        public static Index CATEGORY_IDX_CATEGORY_NAME = Internal.createIndex("idx_category_name", Category.CATEGORY, new OrderField[] { Category.CATEGORY.CATEGORY_NAME }, false);
        public static Index CATEGORY_IDX_PARENT_CODE = Internal.createIndex("idx_parent_code", Category.CATEGORY, new OrderField[] { Category.CATEGORY.PARENT_CODE }, false);
        public static Index CATEGORY_PRIMARY = Internal.createIndex("PRIMARY", Category.CATEGORY, new OrderField[] { Category.CATEGORY.ID }, true);
        public static Index CATEGORY_UNIQ_IDX_UNI_CATEGORY_CODE = Internal.createIndex("uniq_idx_uni_category_code", Category.CATEGORY, new OrderField[] { Category.CATEGORY.CATEGORY_CODE }, true);
        public static Index CHANNEL_IDX_ENTITY_TYPE_CODE = Internal.createIndex("idx_entity_type_code", Channel.CHANNEL, new OrderField[] { Channel.CHANNEL.ENTITY_TYPE, Channel.CHANNEL.ENTITY_CODE }, false);
        public static Index CHANNEL_IDX_PROTOCOL = Internal.createIndex("idx_protocol", Channel.CHANNEL, new OrderField[] { Channel.CHANNEL.PROTOCOL }, false);
        public static Index CHANNEL_IDX_SUPPLIER = Internal.createIndex("idx_supplier", Channel.CHANNEL, new OrderField[] { Channel.CHANNEL.SUPPLIER }, false);
        public static Index CHANNEL_PRIMARY = Internal.createIndex("PRIMARY", Channel.CHANNEL, new OrderField[] { Channel.CHANNEL.ID }, true);
        public static Index CHANNEL_UNIQ_IDX_UNI_CHANNEL_CODE = Internal.createIndex("uniq_idx_uni_channel_code", Channel.CHANNEL, new OrderField[] { Channel.CHANNEL.CHANNEL_CODE }, true);
        public static Index ENDPOINT_PRIMARY = Internal.createIndex("PRIMARY", Endpoint.ENDPOINT, new OrderField[] { Endpoint.ENDPOINT.ID }, true);
        public static Index ENDPOINT_UNIQ_IDX_UNI_ENDPOINT = Internal.createIndex("uniq_idx_uni_endpoint", Endpoint.ENDPOINT, new OrderField[] { Endpoint.ENDPOINT.ENDPOINT_ }, true);
        public static Index ENDPOINT_UNIQ_IDX_UNI_ENDPOINT_CODE = Internal.createIndex("uniq_idx_uni_endpoint_code", Endpoint.ENDPOINT, new OrderField[] { Endpoint.ENDPOINT.ENDPOINT_CODE }, true);
        public static Index ENDPOINT_UNIQ_IDX_UNI_ENDPOINT_NAME = Internal.createIndex("uniq_idx_uni_endpoint_name", Endpoint.ENDPOINT, new OrderField[] { Endpoint.ENDPOINT.ENDPOINT_NAME }, false);
        public static Index ENDPOINT_CATEGORY_REL_IDX_CATEGORY_CODE = Internal.createIndex("idx_category_code", EndpointCategoryRel.ENDPOINT_CATEGORY_REL, new OrderField[] { EndpointCategoryRel.ENDPOINT_CATEGORY_REL.CATEGORY_CODE }, false);
        public static Index ENDPOINT_CATEGORY_REL_IDX_SORT = Internal.createIndex("idx_sort", EndpointCategoryRel.ENDPOINT_CATEGORY_REL, new OrderField[] { EndpointCategoryRel.ENDPOINT_CATEGORY_REL.SORT }, false);
        public static Index ENDPOINT_CATEGORY_REL_PRIMARY = Internal.createIndex("PRIMARY", EndpointCategoryRel.ENDPOINT_CATEGORY_REL, new OrderField[] { EndpointCategoryRel.ENDPOINT_CATEGORY_REL.ID }, true);
        public static Index ENDPOINT_CATEGORY_REL_UNIQ_IDX_UNI_ENDPOINT_CATEGORY_CODE = Internal.createIndex("uniq_idx_uni_endpoint_category_code", EndpointCategoryRel.ENDPOINT_CATEGORY_REL, new OrderField[] { EndpointCategoryRel.ENDPOINT_CATEGORY_REL.ENDPOINT, EndpointCategoryRel.ENDPOINT_CATEGORY_REL.CATEGORY_CODE }, true);
        public static Index MODEL_PRIMARY = Internal.createIndex("PRIMARY", Model.MODEL, new OrderField[] { Model.MODEL.ID }, true);
        public static Index MODEL_UNIQ_IDX_UNI_MODEL_NAME = Internal.createIndex("uniq_idx_uni_model_name", Model.MODEL, new OrderField[] { Model.MODEL.MODEL_NAME }, true);
        public static Index MODEL_ENDPOINT_REL_IDX_MODEL_NAME = Internal.createIndex("idx_model_name", ModelEndpointRel.MODEL_ENDPOINT_REL, new OrderField[] { ModelEndpointRel.MODEL_ENDPOINT_REL.MODEL_NAME }, false);
        public static Index MODEL_ENDPOINT_REL_PRIMARY = Internal.createIndex("PRIMARY", ModelEndpointRel.MODEL_ENDPOINT_REL, new OrderField[] { ModelEndpointRel.MODEL_ENDPOINT_REL.ID }, true);
        public static Index MODEL_ENDPOINT_REL_UNIQ_IDX_UNI_ENDPOINT_MODEL = Internal.createIndex("uniq_idx_uni_endpoint_model", ModelEndpointRel.MODEL_ENDPOINT_REL, new OrderField[] { ModelEndpointRel.MODEL_ENDPOINT_REL.ENDPOINT, ModelEndpointRel.MODEL_ENDPOINT_REL.MODEL_NAME }, true);
    }
}
