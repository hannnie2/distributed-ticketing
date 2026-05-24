-- All KEYS share the {e:E:s:S:r:R} hash tag and live in the same slot.
-- KEYS[1] = bits        ({e:E:s:S:r:R}:bits)
-- KEYS[2] = cap         ({e:E:s:S:r:R}:cap)
-- KEYS[3] = idem        ({e:E:s:S:r:R}:idem:{idemKey})   -- client-supplied idempotency
-- KEYS[4] = hold        ({e:E:s:S:r:R}:hold:{holdId})
-- ARGV[1] = holdId
-- ARGV[2] = idemTtlSeconds
-- ARGV[3..N] = seat offsets (0-based)

local FIXED_ARGV = 2

-- Idempotent retry: same idemKey replayed (Spring Retry, network blip) returns the
-- existing holdId. Different idemKey is always a new request and proceeds independently.
local existing = redis.call('GET', KEYS[3])
if existing then
    if redis.call('EXISTS', '{' .. string.match(KEYS[3], '{(.-)}') .. '}:hold:' .. existing) == 1 then
        return {2, existing}
    end
    -- Idem key outlived its hold (released/sold). Treat as a fresh request.
    redis.call('DEL', KEYS[3])
end

local cap = redis.call('GET', KEYS[2])
if not cap then
    return {0, 'no_capacity'}
end
local capacity = tonumber(cap)

local numSeats = #ARGV - FIXED_ARGV

for i = 1, numSeats do
    local off = tonumber(ARGV[FIXED_ARGV + i])
    if off >= capacity then
        return {0, 'out_of_range'}
    end
    if redis.call('GETBIT', KEYS[1], off) == 1 then
        return {0, 'taken'}
    end
end

for i = 1, numSeats do
    redis.call('SETBIT', KEYS[1], tonumber(ARGV[FIXED_ARGV + i]), 1)
end

-- Hold hash carries only what the orphan reconciler needs to identify and
-- release this hold: the seat offsets.
redis.call('HMSET', KEYS[4],
    'seats', table.concat({unpack(ARGV, FIXED_ARGV + 1)}, ','))

-- Idem key TTLs out on its own; release/convert paths don't need to know about it.
redis.call('SET', KEYS[3], ARGV[1], 'EX', tonumber(ARGV[2]))

return {1, ARGV[1]}
