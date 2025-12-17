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
local EXPIRY_TIME = 240   -- 240秒TTL，确保兜底清理
local BUCKET_SIZE = 10   -- 10秒一个桶
local MAX_BUCKETS = 12    -- 保留最近12个桶(包含当前桶)

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
    local active_buckets_key = "bucket-active:" .. entity
    local bucket_prefix = "bucket:" .. entity .. ":"
    
    -- 清理过期桶 (仅在GET操作时执行)
    if op == "GET" then
        local active_buckets = redis.call("SMEMBERS", active_buckets_key)
        local expired_keys = {}
        local expired_bucket_ids = {}
        
        -- 收集需要清理的桶
        for i = 1, #active_buckets do
            local bucket_id = tonumber(active_buckets[i])
            if bucket_id then
                local bucket_age = current_bucket_id - bucket_id
                -- 年龄>=MAX_BUCKETS的桶需要清理
                if bucket_age >= MAX_BUCKETS then
                    expired_keys[#expired_keys + 1] = bucket_prefix .. bucket_id
                    expired_bucket_ids[#expired_bucket_ids + 1] = bucket_id
                end
            end
        end
        
        -- 批量清理过期桶
        if #expired_keys > 0 then
            redis.call("DEL", unpack(expired_keys))
            redis.call("SREM", active_buckets_key, unpack(expired_bucket_ids))
        end
    end
    
    -- 处理具体操作
    if op == "INCR" then
        -- 桶计数器+1
        local bucket_key = bucket_prefix .. current_bucket_id
        redis.call("INCR", bucket_key)
        redis.call("EXPIRE", bucket_key, EXPIRY_TIME)
        
        -- 维护活跃桶列表
        redis.call("SADD", active_buckets_key, current_bucket_id)
        redis.call("EXPIRE", active_buckets_key, EXPIRY_TIME)
        
    elseif op == "DECR" then
        -- 构建候选桶key列表
        local candidate_keys = {}
        local candidate_bucket_ids = {}
        for i = 0, MAX_BUCKETS - 1 do
            local check_bucket_id = current_bucket_id - i
            candidate_keys[i + 1] = bucket_prefix .. check_bucket_id
            candidate_bucket_ids[i + 1] = check_bucket_id
        end
        
        -- 批量获取桶计数
        local counts = redis.call("MGET", unpack(candidate_keys))
        
        -- 找到第一个可递减的桶
        for i = 1, #counts do
            if counts[i] and tonumber(counts[i]) > 0 then
                local bucket_key = candidate_keys[i]
                local bucket_id = candidate_bucket_ids[i]
                local new_count = redis.call("DECR", bucket_key)
                
                -- 如果桶为空，清理它
                if new_count <= 0 then
                    redis.call("DEL", bucket_key)
                    redis.call("SREM", active_buckets_key, bucket_id)
                end
                
                break -- 找到第一个可递减的桶后停止
            end
        end
    end
    
    -- 只有GET操作才计算真实值，其他操作直接返回0
    if op ~= "GET" then
        return 0
    end
    
    -- 计算并返回总计数
    local updated_buckets = redis.call("SMEMBERS", active_buckets_key)
    local total = 0
    
    if #updated_buckets > 0 then
        -- 构建所有桶的key列表
        local bucket_keys = {}
        for i = 1, #updated_buckets do
            bucket_keys[i] = bucket_prefix .. updated_buckets[i]
        end
        
        -- 使用MGET一次性获取所有桶的计数
        local counts = redis.call("MGET", unpack(bucket_keys))
        for i = 1, #counts do
            if counts[i] then
                total = total + tonumber(counts[i])
            end
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
