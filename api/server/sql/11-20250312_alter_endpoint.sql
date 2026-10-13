SET NAMES utf8mb4;
alter table endpoint add column cost_script varchar(1024) default '' not null comment '计费脚本' after status;
