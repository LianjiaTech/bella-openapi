-- ============================================================================
-- Channel 实时RPM统计脚本（简化版本）
-- ============================================================================
--
-- 功能说明：
--   专注于 RPM 统计功能，EMA 计算迁移到 Java 层
--
-- 参数说明：
--   KEYS[1]: channelCode      - 渠道代码
--   ARGV[1]: timestamp        - 当前时间戳（秒）
--   ARGV[2]: avgResponseTime  - 平均响应时间（毫秒）
-- ============================================================================

local channel_code = KEYS[1]
local current_timestamp = tonumber(ARGV[1])
local avg_response_time = tonumber(ARGV[2]) or 60000

local RPM_KEY_PREFIX = "bella-openapi-channel-realtime-rpm:"
local rpm_key_prefix = RPM_KEY_PREFIX .. channel_code

local WINDOW_SIZE = 60
local RPM_TTL = 120

-- 计算请求占用的分钟数
local function calculate_rpm_minutes(avg_response_time_ms)
    if avg_response_time_ms <= 60000 then
        return 1
    else
        return math.ceil(avg_response_time_ms / 60000)
    end
end

-- 增加实时RPM计数
local function increment_realtime_rpm()
    local rpm_minutes = calculate_rpm_minutes(avg_response_time)

    for i = 0, rpm_minutes - 1 do
        local target_minute = current_timestamp + (i * 60)
        local minute_key = rpm_key_prefix .. ":" .. target_minute
        redis.call("INCR", minute_key)
        redis.call("EXPIRE", minute_key, RPM_TTL)
    end
end

-- 获取当前RPM（60秒滑动窗口）
local function get_current_rpm()
    local window_start = current_timestamp - WINDOW_SIZE
    local total_rpm = 0

    for ts = window_start, current_timestamp do
        local minute_key = rpm_key_prefix .. ":" .. ts
        local count = redis.call("GET", minute_key)
        if count then
            total_rpm = total_rpm + tonumber(count)
        end
    end

    return total_rpm
end

-- 主函数
local function main()
    -- 增加RPM计数
    increment_realtime_rpm()
    
    -- 获取当前RPM
    local current_rpm = get_current_rpm()
    
    return {
        current_rpm = current_rpm,
        timestamp = current_timestamp
    }
end

-- 执行主函数并处理异常
local success, result = pcall(main)

if not success then
    return string.format('{"error":"%s"}', tostring(result))
end

-- 返回简化的JSON结果
return string.format(
    '{"current_rpm":%d,"timestamp":%d}',
    result.current_rpm or 0,
    result.timestamp
)