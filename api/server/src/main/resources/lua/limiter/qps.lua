-- 基于 ZSET 滑动窗口的 QPS 限流
-- 简单可靠，天然支持 Redis Cluster（单 key 操作）
--
-- 输入参数:
--   KEYS[1]: 限流 key (bella-openapi-limiter-qps:{akCode})
--   ARGV[1]: QPS 限制值
--   ARGV[2]: 当前时间戳（毫秒）
--
-- 返回值:
--   [is_allowed, current_count]
--   is_allowed: 1=通过, 0=拒绝
--   current_count: 当前窗口内的请求数

local key = KEYS[1]
local qps_limit = tonumber(ARGV[1])
local current_time_ms = tonumber(ARGV[2])

-- 配置参数
local WINDOW_SIZE_MS = 1000  -- 滑动窗口大小：1秒
local EXPIRY_TIME = 3        -- key 过期时间：3秒

-- 计算窗口起始时间
local window_start_ms = current_time_ms - WINDOW_SIZE_MS

-- 1. 清理过期数据（1秒前的请求）
redis.call('ZREMRANGEBYSCORE', key, 0, window_start_ms)

-- 2. 统计当前窗口内的请求数
local current_count = redis.call('ZCARD', key)

-- 3. 检查是否超过限制
if current_count >= qps_limit then
    return {0, current_count}
end

-- 4. 通过检查，添加当前请求到集合
-- 直接使用毫秒时间戳作为 member（单 APIKey QPS 较低，冲突概率可忽略）
redis.call('ZADD', key, current_time_ms, tostring(current_time_ms))
redis.call('EXPIRE', key, EXPIRY_TIME)

return {1, current_count + 1}
