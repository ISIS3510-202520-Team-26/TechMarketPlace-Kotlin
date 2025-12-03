package com.techmarketplace.data.repository

import com.techmarketplace.data.remote.api.AnalyticsApi
import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.dto.BqQuickViewCountDto
import com.techmarketplace.data.storage.cache.RecommendedMemoryCache
import com.techmarketplace.data.storage.dao.RecommendedBucket
import com.techmarketplace.data.storage.dao.RecommendedDao
import com.techmarketplace.data.storage.dao.RecommendedItemsEntity
import com.techmarketplace.data.storage.dao.toDomain
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class RecommendedRepository(
    private val analyticsApi: AnalyticsApi,
    private val listingApi: ListingApi,
    private val dao: RecommendedDao,
    private val memoryCache: RecommendedMemoryCache,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun refresh(
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        categoriesLimit: Int = DEFAULT_CATEGORY_LIMIT,
        listingsPerCategory: Int = DEFAULT_LISTINGS_PER_CATEGORY
    ): List<RecommendedBucket> = supervisorScope {
        val window = buildWindow(windowDays)
        val quickViewsDeferred = async(Dispatchers.IO) {
            analyticsApi.quickViewPerDayByCategory(window.startIso, window.endIso)
        }
        val categoriesDeferred = async(Dispatchers.IO) { listingApi.getCategories() }

        val quickViews = runCatching { quickViewsDeferred.await() }.getOrDefault(emptyList())
        val categories = runCatching { categoriesDeferred.await() }
            .getOrDefault(emptyList())
            .associate { it.id to it.name }

        val topCategories = withContext(Dispatchers.Default) {
            pickTopCategories(quickViews, categoriesLimit)
        }

        val buckets = if (topCategories.isNotEmpty()) {
            coroutineScope {
                topCategories.map { catId ->
                    async(Dispatchers.IO) {
                        val listings = runCatching {
                            listingApi.searchListings(
                                categoryId = catId,
                                page = 1,
                                pageSize = listingsPerCategory
                            ).items
                        }.getOrDefault(emptyList())
                        RecommendedBucket(
                            categoryId = catId,
                            categoryName = categories[catId],
                            fetchedAt = clock(),
                            listings = listings,
                            source = "quick_view",
                            windowDays = windowDays
                        )
                    }
                }.mapNotNull { task ->
                    runCatching { task.await() }.getOrNull()
                }.filter { it.listings.isNotEmpty() }
            }
        } else {
            emptyList()
        }

        val finalBuckets = when {
            buckets.isNotEmpty() -> buckets
            else -> fallbackFromFeed(listingsPerCategory, windowDays)
        }

        if (finalBuckets.isNotEmpty()) {
            dao.upsertAll(finalBuckets.map { it.toEntity() })
            finalBuckets.forEach { memoryCache.put(it) }
        }
        pruneOld()
        finalBuckets
    }

    suspend fun getOrRefresh(
        maxAgeMillis: Long = DEFAULT_TTL_MILLIS
    ): List<RecommendedBucket> {
        val mem = memoryCache.snapshot().filter { isFresh(it, maxAgeMillis) }
        if (mem.isNotEmpty()) return mem

        val db = dao.getAll().map { it.toDomain() }.filter { isFresh(it, maxAgeMillis) }
        if (db.isNotEmpty()) {
            db.forEach { memoryCache.put(it) }
            return db
        }
        return refresh()
    }

    fun observe(): Flow<List<RecommendedBucket>> =
        dao.observe().map { entities ->
            entities.map { it.toDomain() }
        }

    private fun pickTopCategories(
        rows: List<BqQuickViewCountDto>,
        limit: Int
    ): List<String> {
        if (rows.isEmpty()) return emptyList()
        val totals = rows.groupBy { it.categoryId }
            .mapValues { (_, values) -> values.sumOf { it.count } }
        return totals.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
    }

    private fun buildWindow(days: Int): Window {
        if (days <= 0) return Window(null, null)
        val end = clock()
        val start = end - days.toLong() * 24 * 60 * 60 * 1000L
        return Window(formatIso(start), formatIso(end))
    }

    private fun isFresh(bucket: RecommendedBucket, ttl: Long): Boolean =
        clock() - bucket.fetchedAt <= ttl

    private suspend fun pruneOld() {
        val cutoff = clock() - DEFAULT_MAX_RETENTION_MILLIS
        dao.deleteOlderThan(cutoff)
    }

    private suspend fun fallbackFromFeed(
        listingsPerCategory: Int,
        windowDays: Int
    ): List<RecommendedBucket> {
        val listings = runCatching {
            listingApi.searchListings(page = 1, pageSize = listingsPerCategory * 2).items
        }.getOrDefault(emptyList())
        return if (listings.isEmpty()) emptyList() else listOf(
            RecommendedBucket(
                categoryId = "all",
                categoryName = "Popular",
                fetchedAt = clock(),
                listings = listings,
                source = "fallback_feed",
                windowDays = windowDays
            )
        )
    }

    private fun RecommendedBucket.toEntity(): RecommendedItemsEntity =
        RecommendedItemsEntity(
            categoryId = categoryId,
            categoryName = categoryName,
            fetchedAt = fetchedAt,
            listings = listings,
            source = source,
            windowDays = windowDays
        )

    private fun formatIso(epochMillis: Long): String = isoFormatter.format(Date(epochMillis))

    private data class Window(val startIso: String?, val endIso: String?)

    companion object {
        private const val DEFAULT_WINDOW_DAYS = 30
        private const val DEFAULT_CATEGORY_LIMIT = 4
        private const val DEFAULT_LISTINGS_PER_CATEGORY = 8
        private const val DEFAULT_TTL_MILLIS = 5 * 60 * 1000L // 5 min
        private const val DEFAULT_MAX_RETENTION_MILLIS = 24 * 60 * 60 * 1000L // 24h

        private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
