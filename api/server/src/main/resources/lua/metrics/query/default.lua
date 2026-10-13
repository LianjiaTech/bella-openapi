-- 查询通道的metric数据
-- 输入参数: channel_codes数组在KEYS中，时间戳和指标名称在ARGV中
-- KEYS[1..n]: channel_codes
-- ARGV[1..n]: metric_names

-- 获取指标名称列表
local metric_names = {}
for i = 1, #ARGV do
    table.insert(metric_names, ARGV[i])
end

-- 错误处理函数
local function handle_error(err)
    return { error = "An error occurred: " .. tostring(err) }
end

-- 获取单个channel的指标数据
local function get_channel_metrics(channel_id)
    local prefix_key = "bella-openapi-channel-metrics:" .. channel_id
    local total_key = prefix_key .. ":total"
    local mark_key = prefix_key .. ":unavailable"

    -- 检查 total_key 是否存在
    local exists = redis.call("EXISTS", total_key)
    if exists == 0 then
        return nil
    end

    local result = {}

    local unavailable = redis.call("GET", mark_key)
    if unavailable then
        result.status = 0
    else
        result.status = 1
    end

    for _, metric_name in ipairs(metric_names) do
        local value = redis.call("HGET", total_key, metric_name)
        if value then
            result[metric_name] = tonumber(value) or 0
        end
    end

    return result
end

-- 获取所有channel的指标数据
local function get_all_channels_metrics()
    local result = {}
    for i = 1, #KEYS do
        local channel_id = KEYS[i]
        local channel_metrics = get_channel_metrics(channel_id)
        if channel_metrics then
            result[channel_id] = channel_metrics
        end
    end
    return result
end

-- 执行查询并返回结果
local success, result = pcall(get_all_channels_metrics)
if not success then
    return handle_error(result)
end

return cjson.encode(result)
