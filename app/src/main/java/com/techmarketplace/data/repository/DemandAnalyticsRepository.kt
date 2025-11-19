package com.techmarketplace.data.repository

import com.techmarketplace.data.remote.api.AnalyticsApi
import com.techmarketplace.data.remote.api.ListingApi
import com.techmarketplace.data.remote.dto.BqButtonCountDto
import com.techmarketplace.data.remote.dto.BqCategoryCountDto
import com.techmarketplace.data.remote.dto.BqQuickViewCountDto
import com.techmarketplace.data.storage.cache.DemandSnapshotMemoryCache
import com.techmarketplace.data.storage.dao.DemandButtonStatEntity
import com.techmarketplace.data.storage.dao.DemandCategoryStatEntity
import com.techmarketplace.data.storage.dao.SellerDemandDao
import com.techmarketplace.data.storage.dao.SellerDemandSnapshotEntity
import com.techmarketplace.data.storage.dao.toDomain
import com.techmarketplace.domain.demand.SellerDemandSnapshot
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DemandAnalyticsRepository(
    private val analyticsApi: AnalyticsApi,
    private val listingApi: ListingApi,
    private val demandDao: SellerDemandDao,
    private val memoryCache: DemandSnapshotMemoryCache,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val nowInstant: () -> Instant = { Instant.now() }
) {

    fun observeSnapshot(sellerId: String): Flow<SellerDemandSnapshot?> =
        demandDao.observeSnapshot(sellerId).map { entity ->
            entity?.toDomain()?.also { memoryCache.put(it) }
        }

    suspend fun getSnapshot(sellerId: String): SellerDemandSnapshot? {
        val cached = memoryCache.get(sellerId)
        if (cached != null) return cached
        return demandDao.getSnapshot(sellerId)?.toDomain()?.also { memoryCache.put(it) }
    }

    suspend fun refreshSnapshot(
        sellerId: String,
        windowDays: Int = DEFAULT_WINDOW_DAYS
    ) {
        val window = buildWindow(windowDays)
        val listings = listingApi.searchListings(mine = true, page = 1, pageSize = 50)
        val sellerCategories = listings.items.map { it.categoryId }.toSet()
        val categories = listingApi.getCategories().associate { it.id to it.name }

        val trending = analyticsApi.listingsPerDayByCategory(window.startIso, window.endIso)
        val buttons = analyticsApi.clicksPerDayByButton(window.startIso, window.endIso)
        val quickViews = analyticsApi.quickViewPerDayByCategory(window.startIso, window.endIso)

        val trendingStats = mapCategoryStats(
            rows = trending,
            categoryNames = categories,
            sellerCategoryIds = sellerCategories,
            categorySelector = { it.categoryId },
            countSelector = { it.count }
        )
        val quickViewStats = mapCategoryStats(
            rows = quickViews,
            categoryNames = categories,
            sellerCategoryIds = sellerCategories,
            categorySelector = { it.categoryId },
            countSelector = { it.count }
        )
        val buttonStats = mapButtonStats(buttons)

        val entity = SellerDemandSnapshotEntity(
            sellerId = sellerId,
            fetchedAt = clock(),
            trendingCategories = trendingStats,
            quickViewCategories = quickViewStats,
            buttonStats = buttonStats,
            sellerCategories = sellerCategories.toList()
        )
        demandDao.upsert(entity)
        memoryCache.put(entity.toDomain())
    }

    fun isFresh(snapshot: SellerDemandSnapshot, ttlMillis: Long): Boolean =
        clock() - snapshot.fetchedAt <= ttlMillis

    private fun <T> mapCategoryStats(
        rows: List<T>,
        categoryNames: Map<String, String>,
        sellerCategoryIds: Set<String>,
        categorySelector: (T) -> String,
        countSelector: (T) -> Int
    ): List<DemandCategoryStatEntity> {
        if (rows.isEmpty()) return emptyList()
        val totals = rows.groupBy(categorySelector)
            .mapValues { (_, values) -> values.sumOf(countSelector) }
        val maxCount = totals.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1

        return totals.entries
            .sortedByDescending { it.value }
            .take(TOP_CATEGORY_LIMIT)
            .map { (categoryId, count) ->
                DemandCategoryStatEntity(
                    categoryId = categoryId,
                    categoryName = categoryNames[categoryId] ?: categoryId,
                    totalCount = count,
                    share = count.toDouble() / maxCount,
                    isSellerCategory = categoryId in sellerCategoryIds
                )
            }
    }

    private fun mapButtonStats(rows: List<BqButtonCountDto>): List<DemandButtonStatEntity> {
        if (rows.isEmpty()) return emptyList()
        val totals = rows.groupBy { it.button }
            .mapValues { (_, values) -> values.sumOf { it.count } }
        return totals.entries
            .sortedByDescending { it.value }
            .take(TOP_BUTTON_LIMIT)
            .map { (button, count) ->
                val label = button?.takeUnless { it.isBlank() } ?: "unknown"
                DemandButtonStatEntity(button = label, totalCount = count)
            }
    }

    private data class Window(val startIso: String?, val endIso: String?)

    private fun buildWindow(days: Int): Window {
        if (days <= 0) return Window(null, null)
        val formatter = DateTimeFormatter.ISO_INSTANT
        val end = nowInstant()
        val start = end.minusSeconds(days.toLong() * 24 * 60 * 60)
        return Window(formatter.format(start), formatter.format(end))
    }

    companion object {
        private const val DEFAULT_WINDOW_DAYS = 14
        private const val TOP_CATEGORY_LIMIT = 6
        private const val TOP_BUTTON_LIMIT = 5
    }
}
