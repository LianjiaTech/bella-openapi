-- 分段滑动窗口 QPS 限流器
-- 将 1 秒窗口分成多个段，使用 Hash 存储计数
--
-- 输入参数:
--   KEYS[1]: 限流 key
--   ARGV[1]: QPS 限制值
--   ARGV[2]: 当前时间戳（毫秒）
--   ARGV[3]: 段大小（毫秒）
--   ARGV[4]: 段数量
--
-- 返回值: [is_allowed, current_count]

local key = KEYS[1]
local qps_limit = tonumber(ARGV[1])
local current_time_ms = tonumber(ARGV[2])
local SEGMENT_SIZE_MS = tonumber(ARGV[3])
local NUM_SEGMENTS = tonumber(ARGV[4])

local EXPIRY_TIME = math.ceil(SEGMENT_SIZE_MS * NUM_SEGMENTS / 1000) + 2

-- 计算当前段 ID
local current_segment = math.floor(current_time_ms / SEGMENT_SIZE_MS)

-- 构建段 ID 列表
local fields = {}
for i = 0, NUM_SEGMENTS - 1 do
    fields[i + 1] = tostring(current_segment - i)
end

-- 一次 HMGET 读取所有段
local values = redis.call('HMGET', key, unpack(fields))

-- 累加计数
local total = 0
for i, v in ipairs(values) do
    if v then
        total = total + tonumber(v)
    end
end

-- 检查是否超限
if total >= qps_limit then
    return {0, total}
end

-- 通过检查，增加当前段计数
redis.call('HINCRBY', key, current_segment, 1)
redis.call('EXPIRE', key, EXPIRY_TIME)

return {1, total + 1}