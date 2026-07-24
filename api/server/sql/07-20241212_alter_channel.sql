SET NAMES utf8mb4;
alter table channel add column trial_enabled TINYINT(1) default 1 not null comment '是否支持试用' after status;
