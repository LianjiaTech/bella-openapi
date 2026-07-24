SET NAMES utf8mb4;
alter table model add column linked_to varchar(64) default '' not null comment '模型软链' after features;
