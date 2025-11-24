-- Get channel completed request count with automatic expiry cleanup
--
-- This script retrieves the total completed request count for a channel
-- and automatically purges expired data points to maintain data freshness.
--
-- Input parameters:
--   KEYS[1]: channel_code (e.g., "openai-gpt4-turbo")
--   ARGV[1]: current_timestamp (seconds since epoch)
--
-- Returns: Total completed request count in the last 60 seconds

local prefix_key = "bella-openapi-channel-metrics:" .. KEYS[1]
local current_timestamp = tonumber(ARGV[1])

-- Time window for metrics retention (60 seconds)
local EXPIRY_TIME = 60

-- Maximum iterations to prevent infinite loops in case of data corruption
-- With 1-second granularity and 60-second window, this allows generous headroom
local MAX_CLEANUP_ITERATIONS = 100

-- Main logic
local success, result = pcall(function()
    -- Prevent clock skew: use the latest timestamp if current time goes backward
    local timestamps_key = prefix_key .. ":timestamps"
    local last_timestamp = redis.call("LINDEX", timestamps_key, -1)
    if last_timestamp and tonumber(last_timestamp) > current_timestamp then
        current_timestamp = tonumber(last_timestamp)
    end

    -- Clean up expired timestamps and associated data
    local oldest_allowed = current_timestamp - EXPIRY_TIME
    local expired_completed = 0
    local completed_key = prefix_key .. ":completed"
    local status_key = prefix_key .. ":status"

    -- Bounded cleanup loop with iteration limit to prevent timeout
    local iteration_count = 0
    while iteration_count < MAX_CLEANUP_ITERATIONS do
        iteration_count = iteration_count + 1

        -- Check oldest timestamp in the list
        local oldest_timestamp = redis.call("LINDEX", timestamps_key, 0)
        if not oldest_timestamp or tonumber(oldest_timestamp) > oldest_allowed then
            -- No more expired data, exit loop
            break
        end

        -- Accumulate expired completed count for total adjustment
        local expired_value = redis.call("HGET", completed_key, oldest_timestamp)
        if expired_value then
            expired_completed = expired_completed + tonumber(expired_value)
            redis.call("HDEL", completed_key, oldest_timestamp)
        end

        -- Clean up status hash for this timestamp
        redis.call("HDEL", status_key, oldest_timestamp)

        -- Remove expired timestamp from the list
        redis.call("LPOP", timestamps_key)
    end

    -- Update total and return current completed count
    local total_key = prefix_key .. ":total"
    if expired_completed > 0 then
        -- Subtract expired count from total
        local new_total = redis.call("HINCRBY", total_key, "completed", -expired_completed)
        return math.max(0, new_total)
    else
        -- No expired data, return current total
        local total_obj = redis.call("HGET", total_key, "completed")
        return total_obj and tonumber(total_obj) or 0
    end
end)

return success and result or 0