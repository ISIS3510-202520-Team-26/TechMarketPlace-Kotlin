package com.techmarketplace.data.storage.cache

import android.util.LruCache
import com.techmarketplace.data.storage.dao.RecommendedBucket

class RecommendedMemoryCache(
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlMillis: Long = DEFAULT_TTL
) {
    private val cache = object : LruCache<String, RecommendedBucket>(maxEntries) {}

    fun get(categoryId: String): RecommendedBucket? {
        val item = cache.get(categoryId) ?: return null
        val now = System.currentTimeMillis()
        return if (now - item.fetchedAt <= ttlMillis) item else null
    }

    fun put(bucket: RecommendedBucket) {
        cache.put(bucket.categoryId, bucket)
    }

    fun snapshot(): List<RecommendedBucket> =
        cache.snapshot().values.filter { System.currentTimeMillis() - it.fetchedAt <= ttlMillis }

    fun clear() = cache.evictAll()

    companion object {
        private const val DEFAULT_MAX_ENTRIES = 6
        private const val DEFAULT_TTL = 10 * 60 * 1000L // 10 min
    }
}
