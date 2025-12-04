-- 基于滑动窗口计数器的 QPS 限流
-- 输入参数
local key = KEYS[1]  -- bella-openapi-limiter-qps:{akCode}
local qps_limit = tonumber(ARGV[1])  -- QPS 限制
local current_time_ms = tonumber(ARGV[2])  -- 当前时间戳（毫秒）

-- 配置参数
local SLOT_SIZE_MS = 100  -- 时间片大小：100ms
local WINDOW_SIZE_MS = 1000  -- 滑动窗口大小：1秒
local EXPIRY_TIME = 3  -- key 过期时间：3秒

-- 错误处理函数
local function handle_error(err)
    return {0, 0, "An error occurred: " .. tostring(err)}
end

-- 计算时间片编号
local function get_slot(timestamp_ms)
    return math.floor(timestamp_ms / SLOT_SIZE_MS)
end

-- 主要逻辑
local success, result = pcall(function()
    -- 计算当前时间片和窗口起始时间片
    local current_slot = get_slot(current_time_ms)
    local window_start_slot = get_slot(current_time_ms - WINDOW_SIZE_MS)

    -- 1. 清理过期时间片（1秒前的数据）
    local all_slots = redis.call('HKEYS', key)
    for _, slot in ipairs(all_slots) do
        if tonumber(slot) <= window_start_slot then
            redis.call('HDEL', key, slot)
        end
    end

    -- 2. 统计当前窗口内的请求数
    local count = 0
    for slot = window_start_slot + 1, current_slot do
        local slot_count = redis.call('HGET', key, tostring(slot))
        if slot_count then
            count = count + tonumber(slot_count)
        end
    end

    -- 3. 检查是否超过限制
    if count >= qps_limit then
        -- 拒绝请求，返回 [is_allowed=0, current_count, "rejected"]
        return {0, count, "rejected"}
    end

    -- 4. 通过检查，当前时间片 +1
    redis.call('HINCRBY', key, tostring(current_slot), 1)
    redis.call('EXPIRE', key, EXPIRY_TIME)

    -- 返回 [is_allowed=1, new_count, "allowed"]
    return {1, count + 1, "allowed"}
end)

-- 检查是否有错误发生
if not success then
    return handle_error(result)
end

return result
