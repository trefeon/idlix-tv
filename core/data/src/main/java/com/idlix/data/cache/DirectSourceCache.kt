package com.idlix.data.cache

import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny in-memory TTL map for direct-source reads (no new dependencies).
 *
 * TTLs: lists 15m, details/episodes 30m, genres 2h, search 10m.
 * Play-info/claim/redeem/resolve are NEVER stored here.
 */
class DirectSourceCache(
    private val clock: () -> Long = System::currentTimeMillis
) {

    companion object {
        const val LIST_TTL_MS = 15L * 60L * 1_000L
        const val DETAIL_TTL_MS = 30L * 60L * 1_000L
        const val GENRE_TTL_MS = 2L * 60L * 60L * 1_000L
        const val SEARCH_TTL_MS = 10L * 60L * 1_000L
    }
    private data class Entry(val expiresAtMs: Long, val value: Any)

    private val map = ConcurrentHashMap<String, Entry>()

    /** Type-checked read; wrong-type hits return null instead of throwing. */
    fun <T : Any> get(key: String, type: Class<T>): T? {
        val entry = map[key] ?: return null
        if (entry.expiresAtMs <= clock()) {
            map.remove(key, entry)
            return null
        }
        return if (type.isInstance(entry.value)) type.cast(entry.value) else null
    }

    fun put(key: String, value: Any, ttlMs: Long) {
        if (ttlMs <= 0) return
        map[key] = Entry(clock() + ttlMs, value)
    }


    fun clear() {
        map.clear()
    }

    fun size(): Int = map.size
}
