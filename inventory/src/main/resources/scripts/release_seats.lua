-- KEYS[1] = bits        ({e:E:s:S:r:R}:bits)
-- KEYS[2] = hold        ({e:E:s:S:r:R}:hold:{holdId})
-- ARGV    = seat offsets (0-based)
--
-- The client-supplied idem key (from hold_seats.lua) is intentionally not touched
-- here. It TTLs out on its own; if a stale retry comes in after release, hold_seats
-- detects the missing hold hash and falls through to create a fresh hold.

for i = 1, #ARGV do
    redis.call('SETBIT', KEYS[1], tonumber(ARGV[i]), 0)
end

redis.call('DEL', KEYS[2])

return 1
