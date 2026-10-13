-- 1. 备份数据
CREATE TABLE channel_backup AS
SELECT *
FROM channel;

-- 2. 执行转化
UPDATE channel c
SET price_info = JSON_MERGE_PATCH(
    -- 1. 保留原有的 price_info 内容
        c.price_info,
    -- 2. 构建并合并 tiers 结构
        JSON_OBJECT(
                'tiers', JSON_ARRAY(
                JSON_OBJECT(
                        'inputRangePrice',
                        JSON_MERGE_PATCH(
                            -- 默认的基础数据
                                JSON_OBJECT('minToken', 0, 'maxToken', 2147483647),
                            -- 动态提取数据：如果字段不存在，EXTRACT返回NULL，MERGE_PATCH会自动忽略该字段
                                JSON_OBJECT(
                                        'input', JSON_EXTRACT(c.price_info, '$.input'),
                                        'output', JSON_EXTRACT(c.price_info, '$.output'),
                                        'imageInput', JSON_EXTRACT(c.price_info, '$.imageInput'),
                                        'imageOutput', JSON_EXTRACT(c.price_info, '$.imageOutput'),
                                        'cachedRead', JSON_EXTRACT(c.price_info, '$.cachedRead'),
                                        'cachedCreation', JSON_EXTRACT(c.price_info, '$.cachedCreation')
                                )
                        )
                ))
        ))
WHERE EXISTS (SELECT 1
              FROM model_endpoint_rel mer
              WHERE mer.model_name = c.entity_code
                AND mer.endpoint = '/v1/chat/completions')
  AND JSON_VALID(c.price_info);
