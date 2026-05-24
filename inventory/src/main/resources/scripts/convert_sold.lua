-- Called after the DB seat rows transition to SOLD. Bits stay set (a set bit with
-- no hold hash = sold), so the bitmap continues to represent "unavailable" without
-- querying the DB on every hold attempt.
--
-- KEYS[1] = hold        ({e:E:s:S:r:R}:hold:{holdId})
--
-- Idem key is left to its own TTL — see release_seats.lua for rationale.

redis.call('DEL', KEYS[1])

return 1
