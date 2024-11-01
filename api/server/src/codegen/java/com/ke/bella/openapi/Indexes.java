/*
 * This file is generated by jOOQ.
 */
package com.ke.bella.openapi;


import com.ke.bella.openapi.tables.Apikey;
import com.ke.bella.openapi.tables.ApikeyMonthCost;
import com.ke.bella.openapi.tables.Category;
import com.ke.bella.openapi.tables.Channel;
import com.ke.bella.openapi.tables.Endpoint;
import com.ke.bella.openapi.tables.EndpointCategoryRel;
import com.ke.bella.openapi.tables.Model;
import com.ke.bella.openapi.tables.ModelAuthorizerRel;
import com.ke.bella.openapi.tables.ModelEndpointRel;
import com.ke.bella.openapi.tables.SpaceMember;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;


/**
 * A class modelling indexes of tables in the default schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index MODEL_AUTHORIZER_REL_IDX_AUTHORIZER_CODE = Internal.createIndex(DSL.name("idx_authorizer_code"), ModelAuthorizerRel.MODEL_AUTHORIZER_REL, new OrderField[] { ModelAuthorizerRel.MODEL_AUTHORIZER_REL.AUTHORIZER_CODE }, false);
    public static final Index ENDPOINT_CATEGORY_REL_IDX_CATEGORY_CODE = Internal.createIndex(DSL.name("idx_category_code"), EndpointCategoryRel.ENDPOINT_CATEGORY_REL, new OrderField[] { EndpointCategoryRel.ENDPOINT_CATEGORY_REL.CATEGORY_CODE }, false);
    public static final Index CATEGORY_IDX_CATEGORY_NAME = Internal.createIndex(DSL.name("idx_category_name"), Category.CATEGORY, new OrderField[] { Category.CATEGORY.CATEGORY_NAME }, false);
    public static final Index CHANNEL_IDX_ENTITY_TYPE_CODE = Internal.createIndex(DSL.name("idx_entity_type_code"), Channel.CHANNEL, new OrderField[] { Channel.CHANNEL.ENTITY_TYPE, Channel.CHANNEL.ENTITY_CODE }, false);
    public static final Index SPACE_MEMBER_IDX_MEMBER_UID = Internal.createIndex(DSL.name("idx_member_uid"), SpaceMember.SPACE_MEMBER, new OrderField[] { SpaceMember.SPACE_MEMBER.MEMBER_UID }, false);
    public static final Index MODEL_ENDPOINT_REL_IDX_MODEL_NAME = Internal.createIndex(DSL.name("idx_model_name"), ModelEndpointRel.MODEL_ENDPOINT_REL, new OrderField[] { ModelEndpointRel.MODEL_ENDPOINT_REL.MODEL_NAME }, false);
    public static final Index APIKEY_MONTH_COST_IDX_MONTH = Internal.createIndex(DSL.name("idx_month"), ApikeyMonthCost.APIKEY_MONTH_COST, new OrderField[] { ApikeyMonthCost.APIKEY_MONTH_COST.MONTH }, false);
    public static final Index MODEL_IDX_OWNER_NAME = Internal.createIndex(DSL.name("idx_owner_name"), Model.MODEL, new OrderField[] { Model.MODEL.OWNER_NAME }, false);
    public static final Index APIKEY_IDX_OWNER_TYPE_CODE = Internal.createIndex(DSL.name("idx_owner_type_code"), Apikey.APIKEY, new OrderField[] { Apikey.APIKEY.OWNER_TYPE, Apikey.APIKEY.OWNER_CODE }, false);
    public static final Index MODEL_IDX_OWNER_TYPE_CODE = Internal.createIndex(DSL.name("idx_owner_type_code"), Model.MODEL, new OrderField[] { Model.MODEL.OWNER_TYPE, Model.MODEL.OWNER_CODE }, false);
    public static final Index APIKEY_IDX_PARENT_OUT_ENTITY_CODE = Internal.createIndex(DSL.name("idx_parent_out_entity_code"), Apikey.APIKEY, new OrderField[] { Apikey.APIKEY.PARENT_CODE, Apikey.APIKEY.OUT_ENTITY_CODE }, false);
    public static final Index CHANNEL_IDX_PROTOCOL = Internal.createIndex(DSL.name("idx_protocol"), Channel.CHANNEL, new OrderField[] { Channel.CHANNEL.PROTOCOL }, false);
    public static final Index ENDPOINT_CATEGORY_REL_IDX_SORT = Internal.createIndex(DSL.name("idx_sort"), EndpointCategoryRel.ENDPOINT_CATEGORY_REL, new OrderField[] { EndpointCategoryRel.ENDPOINT_CATEGORY_REL.SORT }, false);
    public static final Index CHANNEL_IDX_SUPPLIER = Internal.createIndex(DSL.name("idx_supplier"), Channel.CHANNEL, new OrderField[] { Channel.CHANNEL.SUPPLIER }, false);
    public static final Index ENDPOINT_UNIQ_IDX_UNI_ENDPOINT_NAME = Internal.createIndex(DSL.name("uniq_idx_uni_endpoint_name"), Endpoint.ENDPOINT, new OrderField[] { Endpoint.ENDPOINT.ENDPOINT_NAME }, false);
}
