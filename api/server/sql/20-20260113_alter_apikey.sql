SET NAMES utf8mb4;
alter table apikey add column qps_limit
    int default 200 comment 'QPS限制（每秒请求数，0使用系统默认值，负数不限制）'
    after month_quota;
