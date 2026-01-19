-- 1. 备份数据
CREATE TABLE channel_backup AS
SELECT *
FROM channel;

-- 2. 执行转化
UPDATE channel c
SET price_info =
        JSON_MERGE_PATCH(
                JSON_OBJECT(),
                CASE
                    WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.unit')
                        THEN JSON_OBJECT('unit', JSON_EXTRACT(c.price_info, '$.unit'))
                    ELSE JSON_OBJECT() END,
                CASE
                    WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.batchDiscount')
                        THEN JSON_OBJECT('batchDiscount', JSON_EXTRACT(c.price_info, '$.batchDiscount'))
                    ELSE JSON_OBJECT() END,
                CASE
                    WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.input')
                        THEN JSON_OBJECT('input', JSON_EXTRACT(c.price_info, '$.input'))
                    ELSE JSON_OBJECT() END,
                CASE
                    WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.output')
                        THEN JSON_OBJECT('output', JSON_EXTRACT(c.price_info, '$.output'))
                    ELSE JSON_OBJECT() END,
                CASE
                    WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.imageInput')
                        THEN JSON_OBJECT('imageInput', JSON_EXTRACT(c.price_info, '$.imageInput'))
                    ELSE JSON_OBJECT() END,
                CASE
                    WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.imageOutput')
                        THEN JSON_OBJECT('imageOutput', JSON_EXTRACT(c.price_info, '$.imageOutput'))
                    ELSE JSON_OBJECT() END,
                CASE
                    WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.cachedRead')
                        THEN JSON_OBJECT('cachedRead', JSON_EXTRACT(c.price_info, '$.cachedRead'))
                    ELSE JSON_OBJECT() END,
                CASE
                    WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.cachedCreation')
                        THEN JSON_OBJECT('cachedCreation', JSON_EXTRACT(c.price_info, '$.cachedCreation'))
                    ELSE JSON_OBJECT() END,
                JSON_OBJECT(
                        'tiers',
                        JSON_ARRAY(
                                JSON_MERGE_PATCH(
                                        JSON_OBJECT(
                                                'inputRangePrice',
                                                JSON_OBJECT(
                                                        'minToken', 0,
                                                        'maxToken', 2147483647
                                                )
                                        ),
                                        CASE
                                            WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.input')
                                                THEN JSON_OBJECT(
                                                    'inputRangePrice',
                                                    JSON_OBJECT('input', JSON_EXTRACT(c.price_info, '$.input'))
                                                     )
                                            ELSE JSON_OBJECT() END,
                                        CASE
                                            WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.output')
                                                THEN JSON_OBJECT(
                                                    'inputRangePrice',
                                                    JSON_OBJECT('output', JSON_EXTRACT(c.price_info, '$.output'))
                                                     )
                                            ELSE JSON_OBJECT() END,
                                        CASE
                                            WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.imageInput')
                                                THEN JSON_OBJECT(
                                                    'inputRangePrice',
                                                    JSON_OBJECT('imageInput',
                                                                JSON_EXTRACT(c.price_info, '$.imageInput'))
                                                     )
                                            ELSE JSON_OBJECT() END,
                                        CASE
                                            WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.imageOutput')
                                                THEN JSON_OBJECT(
                                                    'inputRangePrice',
                                                    JSON_OBJECT('imageOutput',
                                                                JSON_EXTRACT(c.price_info, '$.imageOutput'))
                                                     )
                                            ELSE JSON_OBJECT() END,
                                        CASE
                                            WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.cachedRead')
                                                THEN JSON_OBJECT(
                                                    'inputRangePrice',
                                                    JSON_OBJECT('cachedRead',
                                                                JSON_EXTRACT(c.price_info, '$.cachedRead'))
                                                     )
                                            ELSE JSON_OBJECT() END,
                                        CASE
                                            WHEN JSON_CONTAINS_PATH(c.price_info, 'one', '$.cachedCreation')
                                                THEN JSON_OBJECT(
                                                    'inputRangePrice',
                                                    JSON_OBJECT('cachedCreation',
                                                                JSON_EXTRACT(c.price_info, '$.cachedCreation'))
                                                     )
                                            ELSE JSON_OBJECT() END
                                )
                        )
                )
        )
WHERE EXISTS (SELECT 1
              FROM model_endpoint_rel mer
              WHERE mer.model_name = c.entity_code
                AND mer.endpoint = '/v1/chat/completions')
  AND JSON_VALID(c.price_info);
