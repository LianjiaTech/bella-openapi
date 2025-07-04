-- 实时RPM计算脚本，在请求开始时调用
-- 输入参数
local channel_id = KEYS[1]
local current_timestamp = tonumber(ARGV[1])  -- 当前时间戳(秒)
local avg_response_time = tonumber(ARGV[2])   -- 平均响应时间(毫秒)

-- key的前缀
local prefix_key = "bella-openapi-channel-realtime-rpm:" .. channel_id

-- 滑动窗口大小（60秒）
local WINDOW_SIZE = 60

-- 计算需要增加RPM的分钟数
local function calculate_rpm_minutes(avg_response_time_ms)
    if avg_response_time_ms <= 60000 then  -- <= 1分钟
        return 1
    else
        return math.ceil(avg_response_time_ms / 60000)  -- 向上取整
    end
end

-- 增加实时RPM
local function increment_realtime_rpm()
    local rpm_minutes = calculate_rpm_minutes(avg_response_time or 60000)  -- 默认1分钟
    
    for i = 0, rpm_minutes - 1 do
        local target_minute = current_timestamp + (i * 60)
        local minute_key = prefix_key .. ":" .. target_minute
        redis.call("INCR", minute_key)
        redis.call("EXPIRE", minute_key, WINDOW_SIZE + 60)  -- 多给60秒缓冲
    end
end

-- 清理过期数据
local function cleanup_expired_data()
    local oldest_allowed = current_timestamp - WINDOW_SIZE
    -- 查找需要清理的keys
    local pattern = prefix_key .. ":*"
    local keys = redis.call("KEYS", pattern)
    
    for _, key in ipairs(keys) do
        local timestamp = string.match(key, ":(%d+)$")
        if timestamp and tonumber(timestamp) < oldest_allowed then
            redis.call("DEL", key)
        end
    end
end

-- 主逻辑
local success, result = pcall(function()
    increment_realtime_rpm()
    cleanup_expired_data()
    return { "OK" }
end)

if not success then
    return { "Error: " .. tostring(result) }
end

return result