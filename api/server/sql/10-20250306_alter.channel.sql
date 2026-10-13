SET NAMES utf8mb4;
alter table channel add column owner_type   varchar(16)   default '' not null comment '所有者类型（组织/个人）' after status;
alter table channel add column owner_code   varchar(64)   default '' not null comment '所有者系统号' after owner_type;
alter table channel add column owner_name   varchar(16)   default '' not null comment '所有者名称' after owner_code;
alter table channel add column visibility    varchar(64)  default 'public' not null comment '是否公开(private/public)' after owner_name;
