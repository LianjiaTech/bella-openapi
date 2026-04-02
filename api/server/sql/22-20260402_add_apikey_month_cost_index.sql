-- 为 apikey_month_cost 表添加复合索引，优化 LEFT JOIN 查询性能
-- 用途：ApikeyRepo.pageApikeysWithBalance() 方法中的 LEFT JOIN 关联查询
-- 影响：提升分页查询性能，避免全表扫描

-- 索引说明：
-- 1. 覆盖 JOIN 条件：ak_code = APIKEY.CODE
-- 2. 覆盖 WHERE 条件：month = '2026-04'（当前月份参数）
-- 3. 支持索引覆盖查询（amount 字段可直接从索引获取）

-- 检查索引是否已存在
SELECT COUNT(1)
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'apikey_month_cost'
  AND index_name = 'idx_ak_code_month';

-- 添加复合索引（如果上述查询返回 0，则执行）
ALTER TABLE apikey_month_cost
ADD INDEX idx_ak_code_month (ak_code, month);

-- 验证索引创建结果
SHOW INDEX FROM apikey_month_cost WHERE Key_name = 'idx_ak_code_month';
