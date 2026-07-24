SET NAMES utf8mb4;
create table endpoint
(
    id              bigint(20) not null auto_increment comment '主键ID',
    endpoint        varchar(64) default ''                not null comment '请求path',
    endpoint_code   varchar(64) default ''                not null comment '能力点编码',
    endpoint_name   varchar(64) default ''                not null comment '能力点名称',
    maintainer_code varchar(16) default ''                not null comment '维护人ucid',
    maintainer_name varchar(16) default ''                not null comment '维护人姓名',
    status          varchar(64) default 'active'          not null comment '状态(active/inactive)',
    cuid            bigint(20) default 0 not null comment '创建人id',
    cu_name         varchar(16) default ''                not null comment '创建人姓名',
    muid            bigint(20) default 0 not null comment '编辑人id',
    mu_name         varchar(16) default ''                not null comment '编辑人姓名',
    ctime           timestamp   default CURRENT_TIMESTAMP not null,
    mtime           timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_uni_endpoint_code` (`endpoint_code`),
    unique key `uniq_idx_uni_endpoint` (`endpoint`),
    key             `uniq_idx_uni_endpoint_name` (`endpoint_name`)
)engine=InnoDB default charset=utf8mb4 comment='能力点';


create table model
(
    id           bigint(20) not null auto_increment comment '主键ID',
    model_name   varchar(64)   default ''                not null comment '模型名称',
    document_url varchar(256)  default ''                not null comment '文档地址',
    visibility   varchar(64)   default 'private'         not null comment '是否公开(private/public)',
    owner_type   varchar(16)   default ''                not null comment '所有者类型（系统/组织/个人）',
    owner_code   varchar(64)   default ''                not null comment '所有者系统号',
    owner_name   varchar(16)   default ''                not null comment '所有者名称',
    status       varchar(64)   default 'active'          not null comment '状态(active/inactive)',
    properties   varchar(1024) default '{}'              not null comment '属性',
    features     varchar(1024) default '{}'              not null comment '特性',
    cuid         bigint(20) default 0 not null comment '创建人id',
    cu_name      varchar(16)   default ''                not null comment '创建人姓名',
    muid         bigint(20) default 0 not null comment '编辑人id',
    mu_name      varchar(16)   default ''                not null comment '编辑人姓名',
    ctime        timestamp     default CURRENT_TIMESTAMP not null,
    mtime        timestamp     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_uni_model_name` (`model_name`),
    key          `idx_owner_type_code` (`owner_type`, `owner_code`),
    key          `idx_owner_name` (`owner_name`)
)engine=InnoDB default charset=utf8mb4 comment='模型';

create table model_endpoint_rel
(
    id         bigint(20) not null auto_increment comment '主键ID',
    model_name varchar(64) default ''                not null comment '模型名称',
    endpoint   varchar(64) default ''                not null comment '请求path',
    cuid       bigint(20) default 0 not null comment '创建人id',
    cu_name    varchar(16) default ''                not null comment '创建人姓名',
    muid       bigint(20) default 0 not null comment '编辑人id',
    mu_name    varchar(16) default ''                not null comment '编辑人姓名',
    ctime      timestamp   default CURRENT_TIMESTAMP not null,
    mtime      timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_uni_endpoint_model` (`endpoint`, `model_name`),
    key        `idx_model_name` (`model_name`)
)engine=InnoDB default charset=utf8mb4 comment='模型能力点';

create table model_authorizer_rel
(
    id              bigint(20) not null auto_increment comment '主键ID',
    model_name      varchar(64) default ''                not null comment '模型名称',
    authorizer_type varchar(16) default ''                not null comment '所有者类型（组织/个人）',
    authorizer_code varchar(64) default ''                not null comment '所有者系统号',
    authorizer_name varchar(16) default ''                not null comment '所有者名称',
    cuid            bigint(20) default 0 not null comment '创建人id',
    cu_name         varchar(16) default ''                not null comment '创建人姓名',
    muid            bigint(20) default 0 not null comment '编辑人id',
    mu_name         varchar(16) default ''                not null comment '编辑人姓名',
    ctime           timestamp   default CURRENT_TIMESTAMP not null,
    mtime           timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_uni_model_name_authorizer_code` (`model_name`, `authorizer_code`),
    key             `idx_authorizer_code` (`authorizer_code`)
)engine=InnoDB default charset=utf8mb4 comment='模型授权信息';


create table channel
(
    id               bigint(20) not null auto_increment comment '主键ID',
    entity_type      varchar(64)  default 'model'           not null comment '实体类型（endpoint/model）',
    entity_code      varchar(64)  default ''                not null comment '实体编码',
    channel_code     varchar(64)  default ''                not null comment '渠道编码',
    status           varchar(64)  default 'active'          not null comment '状态状态(active/inactive)',
    data_destination varchar(64)  default 'inner'           not null comment '数据流向(inner/mainland/overseas)',
    priority         varchar(64)  default 'normal'          not null comment '优先级(high/normal/low)',
    protocol         varchar(64)  default ''                not null comment '协议',
    supplier         varchar(64)  default ''                not null comment '服务商',
    url              varchar(512) default ''                not null comment '请求通道的url',
    channel_info     varchar(512) default '{}'              not null comment '渠道信息',
    price_info       varchar(256) default '{}'              not null comment '单价',
    cuid             bigint(20) default 0 not null comment '创建人id',
    cu_name          varchar(16)  default ''                not null comment '创建人姓名',
    muid             bigint(20) default 0 not null comment '编辑人id',
    mu_name          varchar(16)  default ''                not null comment '编辑人姓名',
    ctime            timestamp    default CURRENT_TIMESTAMP not null,
    mtime            timestamp    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_uni_channel_code` (`channel_code`),
    key              `idx_entity_type_code` (`entity_type`,`entity_code`),
    key              `idx_protocol` (`protocol`),
    key              `idx_supplier` (`supplier`)
)engine=InnoDB default charset=utf8mb4 comment='通道';

create table category
(
    id            bigint(20) not null auto_increment comment '主键ID',
    category_code varchar(128) default ''                not null comment '类目编码',
    category_name varchar(64)  default ''                not null comment '类目名',
    parent_code   varchar(128) default ''                not null comment '父类目编码',
    status        varchar(64)  default 'active'          not null comment '状态(active/inactive)',
    cuid          bigint(20) default 0 not null comment '创建人id',
    cu_name       varchar(16)  default ''                not null comment '创建人姓名',
    muid          bigint(20) default 0 not null comment '编辑人id',
    mu_name       varchar(16)  default ''                not null comment '编辑人姓名',
    ctime         timestamp    default CURRENT_TIMESTAMP not null,
    mtime         timestamp    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_uni_category_code` (`category_code`),
    unique key `uniq_idx_parent_code_category_name` (`parent_code`, `category_name`),
    key           `idx_category_name` (`category_name`)
)engine=InnoDB default charset=utf8mb4 comment='类目';

create table endpoint_category_rel
(
    id            bigint(20) not null auto_increment comment '主键ID',
    endpoint      varchar(64)  default ''                not null comment '能力点',
    category_code varchar(128) default ''                not null comment '类目编码',
    sort          int(10) default 0 not null comment '排序',
    cuid          bigint(20) default 0 not null comment '创建人id',
    cu_name       varchar(16)  default ''                not null comment '创建人姓名',
    muid          bigint(20) default 0 not null comment '编辑人id',
    mu_name       varchar(16)  default ''                not null comment '编辑人姓名',
    ctime         timestamp    default CURRENT_TIMESTAMP not null,
    mtime         timestamp    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_uni_endpoint_category_code` (`endpoint`,`category_code`),
    key           `idx_category_code` (`category_code`),
    key           `idx_sort` (`sort`)
)engine=InnoDB default charset=utf8mb4 comment='能力点类目';
