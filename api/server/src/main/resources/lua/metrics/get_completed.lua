-- 获取通道完成数量，自动剔除过期数据
-- 输入参数: KEYS[1]=channel_code, ARGV[1]=current_timestamp
local prefix_key = "bella-openapi-channel-metrics:" .. KEYS[1]
local current_timestamp = tonumber(ARGV[1])
local EXPIRY_TIME = 60

-- 主逻辑
local success, result = pcall(function()
    -- 获取最新时间戳，防止时间倒退
    local timestamps_key = prefix_key .. ":timestamps"
    local last_timestamp = redis.call("LINDEX", timestamps_key, -1)
    if last_timestamp and tonumber(last_timestamp) > current_timestamp then
        current_timestamp = tonumber(last_timestamp)
    end
    
    -- 清理过期时间戳和数据
    local oldest_allowed = current_timestamp - EXPIRY_TIME
    local expired_completed = 0
    local completed_key = prefix_key .. ":completed"
    local status_key = prefix_key .. ":status"
    
    while true do
        local oldest_timestamp = redis.call("LINDEX", timestamps_key, 0)
        if not oldest_timestamp or tonumber(oldest_timestamp) > oldest_allowed then
            break
        end
        
        -- 累加过期数据并删除
        local expired_value = redis.call("HGET", completed_key, oldest_timestamp)
        if expired_value then
            expired_completed = expired_completed + tonumber(expired_value)
            redis.call("HDEL", completed_key, oldest_timestamp)
        end
        
        -- 清理状态数据
        redis.call("HDEL", status_key, oldest_timestamp)
        
        -- 移除过期时间戳
        redis.call("LPOP", timestamps_key)
    end
    
    -- 更新总量并返回
    local total_key = prefix_key .. ":total"
    if expired_completed > 0 then
        local new_total = redis.call("HINCRBY", total_key, "completed", -expired_completed)
        return math.max(0, new_total)
    else
        local total_obj = redis.call("HGET", total_key, "completed")
        return total_obj and tonumber(total_obj) or 0
    end
end)

return success and result or 0