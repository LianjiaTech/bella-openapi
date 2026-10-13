-- 使用滑动窗口记录每分钟请求数
-- 输入参数
local key = KEYS[1]
local count_key = KEYS[2]
local current_timestamp = tonumber(ARGV[1])
local request_id = ARGV[2]

-- 一分钟的时间窗口（以秒为单位）
local WINDOW_SIZE = 60
-- 主key过期时间设置为2分钟
local EXPIRY_TIME = 120
-- count key过期时间为1分钟
local COUNT_EXPIRY_TIME = 60

-- 错误处理函数
local function handle_error(err)
    return { "An error occurred: " .. tostring(err) }
end

-- 主要逻辑
local success, result = pcall(function()
    -- 计算一分钟前的时间戳
    local min_timestamp = current_timestamp - WINDOW_SIZE
    
    -- 获取要删除的过期数据数量
    local expired_count = redis.call("ZCOUNT", key, 0, min_timestamp)

    -- 删除一分钟之前的数据
    if expired_count > 0 then
        redis.call("ZREMRANGEBYSCORE", key, 0, min_timestamp)
    end
    
    -- 添加当前时间戳到有序集合，使用requestId作为member防止重复
    local added = redis.call("ZADD", key, current_timestamp, request_id)
    
    -- 计算需要更新的计数值
    local count_change = (added == 1 and 1 or 0) - (expired_count or 0)
    if count_change ~= 0 then
        -- 使用INCRBY更新计数，如果key不存在会自动创建并初始化为0
        local new_count = redis.call("INCRBY", count_key, count_change)
        if new_count < 0 then
            -- 如果计数小于0，删除计数键
            redis.call("DEL", count_key)
        elseif new_count > 0 then
            -- 设置过期时间
            redis.call("EXPIRE", count_key, COUNT_EXPIRY_TIME)
        end
    end

    -- 设置zset key的过期时间
    redis.call("EXPIRE", key, EXPIRY_TIME)
end)

if not success then
    return handle_error(result)
end

return "OK"
