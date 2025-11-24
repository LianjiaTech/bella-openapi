-- Concurrent request counter for API Key and Channel dimensions
--
-- This script atomically updates concurrent request counts for both API Key
-- and Channel levels, ensuring data consistency across dimensions.
--
-- Input parameters:
--   KEYS[1]: API Key concurrent counter key (e.g., "bella-openapi-limiter-concurrent:model:ak123")
--   KEYS[2]: Entity Code for Channel identification (e.g., "openai-gpt4")
--   ARGV[1]: Operation type - "INCR" or "DECR"
--
-- Returns: Updated API Key concurrent count, or error message

local api_key_concurrent_key = KEYS[1]
local entity_code = KEYS[2] -- Entity Code (optional, used as Channel identifier)
local operation = ARGV[1] -- "INCR" or "DECR"

-- Key expiration time set to 2 minutes to prevent deadlock
local EXPIRY_TIME = 120

-- Error handling function
local function handle_error(err)
    return { "An error occurred: " .. tostring(err) }
end

-- Main logic - both operations are atomic within this script
local success, result = pcall(function()
    -- API Key dimension concurrent counting (original logic)
    local api_key_count = redis.call(operation, api_key_concurrent_key)

    -- Clean up negative counts (should not happen in normal operation)
    if api_key_count < 0 then
        redis.call("DEL", api_key_concurrent_key)
        api_key_count = 0
    elseif api_key_count > 0 then
        -- Set expiration time to auto-cleanup stale counters
        redis.call("EXPIRE", api_key_concurrent_key, EXPIRY_TIME)
    end

    -- Channel dimension concurrent counting (new logic, using entity_code as Channel identifier)
    -- This provides channel-level capacity tracking for worker load balancing
    if entity_code and entity_code ~= "" then
        local channel_concurrent_key = "bella-openapi-channel-concurrent:" .. entity_code
        local channel_count = redis.call(operation, channel_concurrent_key)

        -- Clean up negative counts
        if channel_count < 0 then
            redis.call("DEL", channel_concurrent_key)
        elseif channel_count > 0 then
            redis.call("EXPIRE", channel_concurrent_key, EXPIRY_TIME)
        end
    end

    return api_key_count or 0
end)

if not success then
    return handle_error(result)
end

return result
