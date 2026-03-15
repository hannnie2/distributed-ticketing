-- KEYS[1] = row bitmap key          (seats:{eventId}:{section}:{row})
-- KEYS[2] = capacity key            (seats:{eventId}:{section}:{row}:cap)
-- KEYS[3] = hold lookup key         (hold:lookup:{userId}:{eventId})
-- KEYS[4] = hold key                (hold:{holdId})
-- ARGV[1] = holdId
-- ARGV[2] = userId
-- ARGV[3] = eventId
-- ARGV[4] = sectionRow              (e.g. "1:A")
-- ARGV[5] = hold TTL in seconds
-- ARGV[6..N] = seat offsets (0-based)

local FIXED_ARGV = 5

-- check for existing hold (idempotent retry)
local existingHoldId = redis.call('GET', KEYS[3])
if existingHoldId then
    local remainingTtl = redis.call('TTL', 'hold:' .. existingHoldId)
    if remainingTtl > 0 then
        return {2, existingHoldId, remainingTtl}
    end
    -- hold expired but lookup key lingered, clean up and proceed
    redis.call('DEL', KEYS[3])
end

local cap = redis.call('GET', KEYS[2])
if not cap then
    return {0}
end
local capacity = tonumber(cap)

local numSeats = #ARGV - FIXED_ARGV

-- validate all seats are available
for i = 1, numSeats do
    local off = tonumber(ARGV[FIXED_ARGV + i])
    if off >= capacity then
        return {0}
    end
    if redis.call('GETBIT', KEYS[1], off) == 1 then
        return {0}
    end
end

-- flip bits
for i = 1, numSeats do
    local off = tonumber(ARGV[FIXED_ARGV + i])
    redis.call('SETBIT', KEYS[1], off, 1)
end

-- create hold hash
local ttl = tonumber(ARGV[5])
redis.call('HMSET', KEYS[4],
    'userId', ARGV[2],
    'eventId', ARGV[3],
    'row', ARGV[4],
    'seats', table.concat({unpack(ARGV, FIXED_ARGV + 1)}, ','))
redis.call('EXPIRE', KEYS[4], ttl)

-- create lookup key for idempotent retry
redis.call('SET', KEYS[3], ARGV[1], 'EX', ttl)

return {1}
