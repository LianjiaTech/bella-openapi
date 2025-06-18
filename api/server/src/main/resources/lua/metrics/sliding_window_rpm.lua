-- 滑动窗口RPM实时计算脚本
-- 输入参数
local channel_id = KEYS[1]
local current_timestamp = tonumber(ARGV[1])  -- 当前时间戳(s)
local avg_response_time = tonumber(ARGV[2])  -- 平均响应时间(ms)

-- key的前缀
local prefix_key = "bella-openapi-channel-realtime-rpm:" .. channel_id

-- 滑动窗口时间（60秒）
local WINDOW_TIME = 60

-- 错误处理函数
local function handle_error(err)
    return { "An error occurred: " .. tostring(err) }
end

-- 计算需要增加RPM的分钟数
local function calculate_rpm_minutes(response_time_ms)
    if response_time_ms <= 60000 then
        return 1
    else
        return math.ceil(response_time_ms / 60000)
    end
end

-- 更新实时RPM
local function update_realtime_rpm()
    local rpm_minutes = calculate_rpm_minutes(avg_response_time)
    
    -- 为当前分钟和后续分钟增加RPM计数
    for i = 0, rpm_minutes - 1 do
        local target_minute = current_timestamp + (i * 60)
        local minute_key = prefix_key .. ":" .. target_minute
        redis.call("INCR", minute_key)
        redis.call("EXPIRE", minute_key, WINDOW_TIME + 60)  -- 多设置60秒防止过期
    end
end

-- 清理过期的RPM数据
local function cleanup_expired_rpm()
    local oldest_allowed = current_timestamp - WINDOW_TIME
    local pattern = prefix_key .. ":*"
    local keys = redis.call("KEYS", pattern)
    
    for _, key in ipairs(keys) do
        local timestamp_str = key:match(":(%d+)$")
        if timestamp_str and tonumber(timestamp_str) < oldest_allowed then
            redis.call("DEL", key)
        end
    end
end

-- 获取当前RPM
local function get_current_rpm()
    local oldest_allowed = current_timestamp - WINDOW_TIME
    local total_requests = 0
    local pattern = prefix_key .. ":*"
    local keys = redis.call("KEYS", pattern)
    
    for _, key in ipairs(keys) do
        local timestamp_str = key:match(":(%d+)$")
        if timestamp_str and tonumber(timestamp_str) >= oldest_allowed then
            local count = redis.call("GET", key)
            if count then
                total_requests = total_requests + tonumber(count)
            end
        end
    end
    
    return total_requests
end

-- 主逻辑
local success, result = pcall(function()
    update_realtime_rpm()
    cleanup_expired_rpm()
    local current_rpm = get_current_rpm()
    
    -- 将当前RPM存储到汇总key中
    local summary_key = prefix_key .. ":current"
    redis.call("SET", summary_key, current_rpm, "EX", 60)
    
    return { "OK", current_rpm }
end)

-- 检查是否有错误发生
if not success then
    return handle_error(result)
end

return result