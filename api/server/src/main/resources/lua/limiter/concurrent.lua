-- 记录并发请求数
-- 输入参数
local key = KEYS[1]
local entity_code = KEYS[2] -- Entity Code（可选，用作Channel标识）
local operation = ARGV[1] -- "INCR" or "DECR"

-- key过期时间设置为2分钟，防止出现死锁
local EXPIRY_TIME = 120

-- 错误处理函数
local function handle_error(err)
    return { "An error occurred: " .. tostring(err) }
end

-- 主要逻辑
local success, result = pcall(function()
    -- API Key维度的并发计数（原有逻辑）
    local current_count = redis.call(operation, key)
    if current_count < 0 then
        redis.call("DEL", key)
    elseif current_count > 0 then
        -- 设置过期时间
        redis.call("EXPIRE", key, EXPIRY_TIME)
    end
    
    -- Channel维度的并发计数（新增逻辑，使用entity_code作为Channel标识）
    if entity_code then
        local channel_concurrent_key = "bella-openapi-channel-concurrent:" .. entity_code
        local channel_count = redis.call(operation, channel_concurrent_key)
        if channel_count < 0 then
            redis.call("DEL", channel_concurrent_key)
        elseif channel_count > 0 then
            redis.call("EXPIRE", channel_concurrent_key, EXPIRY_TIME)
        end
    end
    
    return current_count or 0
end)

if not success then
    return handle_error(result)
end

return result
