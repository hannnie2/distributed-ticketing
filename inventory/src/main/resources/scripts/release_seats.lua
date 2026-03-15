-- KEYS[1] = row bitmap key
-- KEYS[2] = hold lookup key (hold:lookup:{userId}:{eventId}), optional
-- ARGV = list of seat offsets (0-based)

for i = 1, #ARGV do
    local off = tonumber(ARGV[i])
    redis.call('SETBIT', KEYS[1], off, 0)
end

if KEYS[2] then
    redis.call('DEL', KEYS[2])
end

return 1
