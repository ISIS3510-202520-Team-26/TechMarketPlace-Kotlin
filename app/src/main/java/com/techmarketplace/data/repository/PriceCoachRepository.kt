package com.techmarketplace.data.repository

import com.techmarketplace.data.remote.api.AnalyticsApi
import com.techmarketplace.data.remote.api.PriceSuggestionsApi
import com.techmarketplace.data.storage.cache.PriceCoachMemoryCache
import com.techmarketplace.data.storage.dao.PriceCoachDao
import com.techmarketplace.data.storage.dao.PriceCoachSnapshot
import com.techmarketplace.data.storage.dao.PriceCoachSnapshotEntity
import com.techmarketplace.data.storage.dao.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class PriceCoachRepository(
    private val priceApi: PriceSuggestionsApi,
    private val analyticsApi: AnalyticsApi,
    private val dao: PriceCoachDao,
    private val memoryCache: PriceCoachMemoryCache,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun getSnapshot(
        sellerId: String,
        categoryId: String?,
        brandId: String?,
        ttlMillis: Long = DEFAULT_TTL
    ): PriceCoachSnapshot = supervisorScope {
        val cached = memoryCache.get(sellerId)?.takeIf { isFresh(it, ttlMillis) }
        if (cached != null) return@supervisorScope cached

        val db = dao.get(sellerId)?.toDomain()?.takeIf { isFresh(it, ttlMillis) }
        if (db != null) {
            memoryCache.put(db)
            return@supervisorScope db
        }

        val window = DEFAULT_WINDOW_DAYS
        val suggestionDeferred = async(Dispatchers.IO) {
            runCatching { priceApi.getSuggestion(categoryId = categoryId, brandId = brandId) }.getOrNull()
        }
        val gmvDeferred = async(Dispatchers.IO) {
            analyticsApi.gmvByDay(
                startIso = isoFrom(window),
                endIso = isoNow()
            )
        }
        val dauDeferred = async(Dispatchers.IO) {
            analyticsApi.dau(
                startIso = isoFrom(window),
                endIso = isoNow()
            )
        }
        val listingsDeferred = async(Dispatchers.IO) {
            analyticsApi.listingsPerDayByCategory(
                startIso = isoFrom(window),
                endIso = isoNow()
            )
        }

        val suggestion = suggestionDeferred.await()
        val gmvStats = gmvDeferred.await()
        val dauStats = dauDeferred.await()
        val listingsStats = listingsDeferred.await()

        val snapshot = withContext(Dispatchers.Default) {
            PriceCoachSnapshot(
                sellerId = sellerId,
                fetchedAt = clock(),
                categoryId = categoryId,
                brandId = brandId,
                suggestedPriceCents = suggestion?.suggestedPriceCents,
                algorithm = suggestion?.algorithm,
                gmvCents = gmvStats.sumOf { it.gmvCents },
                ordersPaid = gmvStats.sumOf { it.ordersPaid },
                dau = dauStats.sumOf { it.dau },
                listingsInCategory = listingsStats
                    .filter { it.categoryId == categoryId }
                    .sumOf { it.count },
                windowDays = window,
                source = if (suggestion != null || gmvStats.isNotEmpty()) "network" else "cache"
            )
        }

        dao.upsert(snapshot.toEntity())
        memoryCache.put(snapshot)
        pruneOld()
        snapshot
    }

    fun observe(sellerId: String) = dao.observe(sellerId)

    private suspend fun pruneOld() {
        dao.deleteOlderThan(clock() - DEFAULT_RETENTION)
    }

    private fun isFresh(snapshot: PriceCoachSnapshot, ttl: Long): Boolean =
        clock() - snapshot.fetchedAt <= ttl

    private fun isoNow(): String = isoFrom(0)
    private fun isoFrom(daysAgo: Int): String =
        isoFormatter.format(java.util.Date(clock() - daysAgo * DAY_MS))

    private fun PriceCoachSnapshot.toEntity(): PriceCoachSnapshotEntity =
        PriceCoachSnapshotEntity(
            sellerId = sellerId,
            fetchedAt = fetchedAt,
            categoryId = categoryId,
            brandId = brandId,
            suggestedPriceCents = suggestedPriceCents,
            algorithm = algorithm,
            gmvCents = gmvCents,
            ordersPaid = ordersPaid,
            dau = dau,
            listingsInCategory = listingsInCategory,
            windowDays = windowDays
        )

    companion object {
        private const val DEFAULT_WINDOW_DAYS = 30
        private const val DEFAULT_TTL = 5 * 60 * 1000L
        private const val DEFAULT_RETENTION = 24 * 60 * 60 * 1000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private val isoFormatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }
}
