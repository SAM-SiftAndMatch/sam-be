-- Implement thuật toán Token Bucket sử dụng Lua
-- Tại sao dùng Lua?: Redis chạy Lua script dưới dạng Atomic. Loại bỏ hoàn toàn Race Condition.

local key = KEYS[1]
local rate = tonumber(ARGV[1]) -- Tốc độ hồi phục (tokens/giây)
local capacity = tonumber(ARGV[2]) -- Dung lượng tối đa của bucket
local now = tonumber(ARGV[3]) -- Epoch second hiện tại
local requested = tonumber(ARGV[4]) -- Số token yêu cầu

local info = redis.call("HMGET", key, "tokens", "last_refill")
local last_tokens = tonumber(info[1])
local last_refill = tonumber(info[2])

if not last_tokens then
    last_tokens = capacity
    last_refill = now
end

local delta = math.max(0, now - last_refill)
local filled_tokens = math.min(capacity, last_tokens + (delta * rate))

local allowed = false
if filled_tokens >= requested then
    allowed = true
    filled_tokens = filled_tokens - requested
end

redis.call("HMSET", key, "tokens", filled_tokens, "last_refill", now)

-- Tự động hết hạn sau 2 lần thời gian hồi phục đầy xô
redis.call("EXPIRE", key, math.ceil(capacity / rate) * 2)

return allowed
