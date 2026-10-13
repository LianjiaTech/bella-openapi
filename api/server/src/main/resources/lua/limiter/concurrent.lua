-- 并发请求数管理脚本 - 分桶+概率清理方案
-- 支持API Key维度和Channel维度的并发计数
-- 使用时间分桶策略，避免存储具体requestId，大幅减少内存占用
--
-- 输入参数:
--   KEYS[1]: API Key的并发计数key
--   KEYS[2]: Entity Code (Channel标识)
--   ARGV[1]: 操作类型 ("INCR", "DECR", "GET")
--   ARGV[2]: 当前时间戳(秒)
--   ARGV[3]: 请求ID (可选，用于日志记录)

local api_key = KEYS[1]
local entity_code = KEYS[2]
local operation = ARGV[1]
local current_timestamp = tonumber(ARGV[2])

-- 常量定义
local EXPIRY_TIME = 120   -- 120秒TTL，确保兜底清理
local BUCKET_SIZE = 10   -- 10秒一个桶
local MAX_BUCKETS = 6    -- 保留最近6个桶(包含当前桶)

-- API Key操作处理
local function handle_api_key_operation(key, op)
    if not key or key == "" or op == "GET" then
        return 0
    end

    local count = redis.call(op, key)
    
    if count <= 0 then
        redis.call("DEL", key)
        return 0
    else
        redis.call("EXPIRE", key, EXPIRY_TIME)
        return count
    end
end

-- Channel桶操作处理
local function handle_channel_operation(entity, timestamp, op)
    if not entity or not timestamp then
        return 0
    end

    -- 计算当前桶ID和相关key
    local current_bucket_id = math.floor(timestamp / BUCKET_SIZE)
    local bucket_prefix = "bucket:" .. entity .. ":"
    
    -- 处理具体操作
    if op == "INCR" then
        -- 桶计数器+1
        local bucket_key = bucket_prefix .. current_bucket_id
        redis.call("INCR", bucket_key)
        redis.call("EXPIRE", bucket_key, EXPIRY_TIME)
        
    elseif op == "DECR" then
        -- 构建候选桶key列表 (最近MAX_BUCKETS个桶)
        local candidate_keys = {}
        for i = 0, MAX_BUCKETS - 1 do
            local check_bucket_id = current_bucket_id - i
            candidate_keys[i + 1] = bucket_prefix .. check_bucket_id
        end
        
        -- 批量获取桶计数
        local counts = redis.call("MGET", unpack(candidate_keys))
        
        -- 找到第一个可递减的桶
        for i = 1, #counts do
            if counts[i] and tonumber(counts[i]) > 0 then
                local bucket_key = candidate_keys[i]
                local new_count = redis.call("DECR", bucket_key)
                
                -- 如果桶为空，清理它
                if new_count <= 0 then
                    redis.call("DEL", bucket_key)
                end
                
                break -- 找到第一个可递减的桶后停止
            end
        end
    end
    
    -- 只有GET操作才计算真实值，其他操作直接返回0
    if op ~= "GET" then
        return 0
    end
    
    -- 计算并返回总计数 - 检查最近MAX_BUCKETS个桶
    local total = 0
    local keys = {}

    -- 构造所有key的列表
    for i = 0, MAX_BUCKETS - 1 do
        local bucket_id = current_bucket_id - i
        keys[i + 1] = bucket_prefix .. bucket_id
    end

    -- 使用MGET批量获取所有值
    local results = redis.call("MGET", unpack(keys))

    -- 累加所有非空值
    for _, count in ipairs(results) do
        if count then
            total = total + tonumber(count)
        end
    end
    
    return total
end

-- 主逻辑执行
local success, result = pcall(function()
    local api_count = handle_api_key_operation(api_key, operation)
    local channel_count = handle_channel_operation(entity_code, current_timestamp, operation)
    
    -- 优先返回Channel结果，否则返回API Key结果
    return (entity_code and current_timestamp) and channel_count or api_count
end)

return success and result or ("Error: " .. tostring(result))
