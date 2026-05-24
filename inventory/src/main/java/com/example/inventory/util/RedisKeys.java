package com.example.inventory.util;

// All per-row keys share the hash tag {e:E:s:S:r:R} so they live in the same Redis
// Cluster slot and are atomically addressable by a single Lua script.
public final class RedisKeys {
    private RedisKeys() {}

    public static String tag(int eventId, int section, String row) {
        return "{e:" + eventId + ":s:" + section + ":r:" + row + "}";
    }

    public static String bits(int eventId, int section, String row) {
        return tag(eventId, section, row) + ":bits";
    }

    public static String cap(int eventId, int section, String row) {
        return tag(eventId, section, row) + ":cap";
    }

    public static String prices(int eventId, int section, String row) {
        return tag(eventId, section, row) + ":prices";
    }

    public static String hold(int eventId, int section, String row, String holdId) {
        return tag(eventId, section, row) + ":hold:" + holdId;
    }

    // Per-row, client-supplied idempotency key. Same idemKey on retry returns the
    // existing holdId; different idemKey is always treated as a new hold attempt.
    public static String idem(int eventId, int section, String row, String idemKey) {
        return tag(eventId, section, row) + ":idem:" + idemKey;
    }
}
