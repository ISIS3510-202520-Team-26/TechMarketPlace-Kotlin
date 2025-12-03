package com.techmarketplace.data.storage.cache

import android.util.LruCache
import com.techmarketplace.data.storage.dao.PriceCoachSnapshot

class PriceCoachMemoryCache(
    maxEntries: Int = 4,
    private val ttlMillis: Long = DEFAULT_TTL
) {
    private val cache = object : LruCache<String, PriceCoachSnapshot>(maxEntries) {}

    fun get(sellerId: String): PriceCoachSnapshot? {
        val item = cache.get(sellerId) ?: return null
        val now = System.currentTimeMillis()
        return if (now - item.fetchedAt <= ttlMillis) item else null
    }

    fun put(snapshot: PriceCoachSnapshot) {
        cache.put(snapshot.sellerId, snapshot)
    }

    companion object {
        private const val DEFAULT_TTL = 10 * 60 * 1000L // 10 min
    }
}
