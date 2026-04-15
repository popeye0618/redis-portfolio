local stock_key = KEYS[1]
local issued_key = KEYS[2]
local user_id = ARGV[1]

if redis.call('SISMEMBER', issued_key, user_id) == 1 then
    return -2
end

local stock = tonumber(redis.call('GET', stock_key))
if stock == nil or stock <= 0 then
    return -1
end

redis.call('DECR', stock_key)
redis.call('SADD', issued_key, user_id)
return 1