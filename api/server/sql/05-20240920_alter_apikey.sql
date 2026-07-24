SET NAMES utf8mb4;
alter table apikey add column safety_scene_code
    varchar(128)  default '' not null comment '安全认证场景code'
    after certify_code;
