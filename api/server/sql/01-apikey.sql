SET NAMES utf8mb4;
create table apikey
(
    id           bigint(20) not null auto_increment comment '主键ID',
    code         varchar(64)    default ''                not null comment 'ak编码',
    ak_sha       varchar(64)    default ''                not null comment '加密ak',
    ak_display   varchar(64)    default ''                not null comment '脱敏ak',
    name         varchar(64)    default ''                not null comment '名字',
    parent_code  varchar(64)    default ''                not null comment '父ak',
    out_entity_code varchar(64) default ''                not null comment '授权实体code',
    service_id   varchar(64)    default ''                not null comment '服务id',
    owner_type   varchar(16)    default ''                not null comment '所有者类型（系统/组织/个人）',
    owner_code   varchar(64)    default ''                not null comment '所有者系统号',
    owner_name   varchar(16)    default ''                not null comment '所有者名称',
    role_code    varchar(64)    default ''                not null comment '角色编码',
    certify_code varchar(128)   default ''                not null comment '安全认证码',
    safety_level tinyint(2) default 0 not null comment '安全等级',
    month_quota  DECIMAL(10, 2) default 0                 not null comment '每月额度',
    status       varchar(64)    default 'active'          not null comment '状态(active/inactive)',
    remark       varchar(1024)  default ''                not null comment '备注',
    cuid         bigint(20) default 0 not null comment '创建人id',
    cu_name      varchar(16)    default ''                not null comment '创建人姓名',
    muid         bigint(20) default 0 not null comment '编辑人id',
    mu_name      varchar(16)    default ''                not null comment '编辑人姓名',
    ctime        timestamp      default CURRENT_TIMESTAMP not null,
    mtime        timestamp      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_code` (`code`),
    unique key `uniq_idx_ak_sha` (`ak_sha`),
    key          `idx_parent_out_entity_code` (`parent_code`, `out_entity_code`),
    key          `idx_owner_type_code` (`owner_type`, `owner_code`)
)engine=InnoDB default charset=utf8mb4 comment='ak';

create table apikey_role
(
    id         bigint(20) not null auto_increment comment '主键ID',
    role_code  varchar(64) default ''                not null comment 'ak编码',
    path       text                                  not null comment '授权的path',
    cuid       bigint(20) default 0 not null comment '创建人id',
    cu_name    varchar(16) default ''                not null comment '创建人姓名',
    muid       bigint(20) default 0 not null comment '编辑人id',
    mu_name    varchar(16) default ''                not null comment '编辑人姓名',
    ctime      timestamp   default CURRENT_TIMESTAMP not null,
    mtime      timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_role_code` (`role_code`)
)engine=InnoDB default charset=utf8mb4 comment='ak角色';

create table apikey_month_cost
(
    id         bigint(20) not null auto_increment comment '主键ID',
    ak_code    varchar(64)    default ''  not null comment 'ak编码',
    month      varchar(16)    default ''  not null comment '月份',
    amount     DECIMAL(12, 4) default 0   not null comment '开销（分）',
    ctime      timestamp   default CURRENT_TIMESTAMP not null,
    mtime      timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    primary key (id),
    unique key `uniq_idx_ak_code_month` (`ak_code`, `month`),
    key             `idx_month` (`month`)
)engine=InnoDB default charset=utf8mb4 comment='ak月花费';
