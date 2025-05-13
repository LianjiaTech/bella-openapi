SET NAMES utf8mb4;
alter table endpoint add column document_url varchar(255) default '' not null comment '文档地址' after endpoint_name;
