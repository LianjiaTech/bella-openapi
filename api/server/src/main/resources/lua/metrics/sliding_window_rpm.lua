-- sliding_window_rpm.lua: 实时RPM滑动窗口计算
-- 根据请求的平均响应时间计算需要增加RPM的分钟数
-- 参数: channel_id, avg_response_time_ms, current_timestamp
-- 返回: 更新后的当前分钟RPM

local channel_id = ARGV[1]
local avg_response_time_ms = tonumber(ARGV[2])  -- 平均响应时间(毫秒)
local current_timestamp = tonumber(ARGV[3])     -- 当前时间戳(秒)

-- 计算当前分钟的起始时间戳
local current_minute = math.floor(current_timestamp / 60) * 60
local rpm_key = "bella-openapi-realtime-rpm:" .. channel_id

-- 清理60秒之前的过期数据
local expire_before = current_timestamp - 60
redis.call('ZREMRANGEBYSCORE', rpm_key, '-inf', expire_before)

-- 根据平均响应时间计算需要增加RPM的分钟数
local minutes_to_increment = 1
if avg_response_time_ms > 60000 then
    -- 如果响应时间 > 1分钟，计算跨越的分钟数
    minutes_to_increment = math.ceil(avg_response_time_ms / 60000)
end

-- 为当前和之后的分钟增加RPM计数
for i = 0, minutes_to_increment - 1 do
    local minute_timestamp = current_minute + (i * 60)
    redis.call('ZINCRBY', rpm_key, 1, minute_timestamp)
end

-- 设置过期时间为120秒
redis.call('EXPIRE', rpm_key, 120)

-- 返回当前分钟的RPM
local current_rpm = redis.call('ZSCORE', rpm_key, current_minute) or 0
return current_rpm