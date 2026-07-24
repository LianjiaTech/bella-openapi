-- Redis concurrent permit lease script.
-- KEYS[1]: permit key
-- ARGV[1]: operation ("ACQUIRE", "RELEASE")
-- ARGV[2]: max permits for ACQUIRE, permit ID for RELEASE
-- ARGV[3]: permit TTL seconds for ACQUIRE
-- ARGV[4]: permit ID for ACQUIRE
-- ARGV[5]: current timestamp millis for ACQUIRE

local key = KEYS[1]
local operation = ARGV[1]

if operation == "ACQUIRE" then
    local max_permits = tonumber(ARGV[2])
    local ttl = tonumber(ARGV[3])
    local permit_id = ARGV[4]
    local now = tonumber(ARGV[5])

    if not max_permits or max_permits <= 0 or not ttl or ttl <= 0 or not permit_id or permit_id == "" or not now or now <= 0 then
        return 0
    end

    local key_type = redis.call("TYPE", key).ok
    if key_type == "string" then
        local old_ttl = redis.call("PTTL", key)

        if old_ttl > 0 then
            return 0
        else
            redis.call("DEL", key)
            key_type = "none"
        end
    end

    if key_type ~= "none" and key_type ~= "zset" then
        return 0
    end

    local expires_at = now + ttl * 1000
    redis.call("ZREMRANGEBYSCORE", key, "-inf", now)

    if redis.call("ZSCORE", key, permit_id) then
        redis.call("ZADD", key, expires_at, permit_id)
        redis.call("PEXPIRE", key, ttl * 1000)
        return 1
    end

    if redis.call("ZCARD", key) >= max_permits then
        return 0
    end

    redis.call("ZADD", key, expires_at, permit_id)
    redis.call("PEXPIRE", key, ttl * 1000)
    return 1
end

if operation == "RELEASE" then
    local permit_id = ARGV[2]

    if not permit_id or permit_id == "" then
        return 0
    end

    local key_type = redis.call("TYPE", key).ok
    if key_type == "none" then
        return 0
    end
    if key_type ~= "zset" then
        return 0
    end

    local removed = redis.call("ZREM", key, permit_id)
    if redis.call("ZCARD", key) == 0 then
        redis.call("DEL", key)
    end
    return removed
end

return 0
