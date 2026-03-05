    -- KEYS[1] = row bitmap key
    -- ARGV = list of seat offsets (0-based)

    for i = 1, #ARGV do
        local off = tonumber(ARGV[i])
        redis.call('SETBIT', KEYS[1], off, 0)
    end

    return 1
