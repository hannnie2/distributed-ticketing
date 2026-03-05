    -- KEYS[1] = row bitmap key
    -- KEYS[2] = capacity key (seats:{eventId}:{sectionRow}:cap)
    -- ARGV = list of seat offsets (0-based)

    local cap = redis.call('GET', KEYS[2])
    if not cap then
        return 0
    end
    local capacity = tonumber(cap)

    for i = 1, #ARGV do
        local off = tonumber(ARGV[i])
        if off >= capacity then
            return 0
        end
        if redis.call('GETBIT', KEYS[1], off) == 1 then
            return 0
        end
    end

    for i = 1, #ARGV do
        local off = tonumber(ARGV[i])
        redis.call('SETBIT', KEYS[1], off, 1)
    end

    return 1