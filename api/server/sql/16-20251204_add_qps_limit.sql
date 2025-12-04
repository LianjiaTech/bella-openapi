-- 添加 QPS 限流相关字段
-- 日期: 2025-12-04
-- 描述: 为 apikey 表添加 QPS 限制配置字段

SET NAMES utf8mb4;

-- 添加 qps_limit 字段
ALTER TABLE apikey
ADD COLUMN qps_limit INT DEFAULT 100 COMMENT 'QPS限制（每秒请求数，默认100）';

-- 添加索引便于管理后台查询
CREATE INDEX idx_qps_limit ON apikey(qps_limit);

-- 为现有数据设置默认值（如果字段已存在则跳过）
UPDATE apikey SET qps_limit = 100 WHERE qps_limit IS NULL;
