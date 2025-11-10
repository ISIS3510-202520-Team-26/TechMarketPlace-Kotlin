package com.techmarketplace.data.repository

import com.techmarketplace.analytics.ListingTelemetryEvent
import com.techmarketplace.analytics.SearchTelemetryEvent
import com.techmarketplace.data.remote.ApiClient
import com.techmarketplace.data.remote.api.TelemetryApi
import com.techmarketplace.data.remote.api.TelemetryBatch
import com.techmarketplace.data.remote.api.TelemetryEvent
import com.techmarketplace.data.remote.dto.toEntity
import com.techmarketplace.data.storage.dao.SellerMetricsDao
import com.techmarketplace.data.storage.dao.TelemetryDatabaseProvider
import com.techmarketplace.data.storage.dao.toDomain
import com.techmarketplace.data.telemetry.TelemetryAnalytics
import com.techmarketplace.domain.telemetry.SellerResponseMetrics
import com.techmarketplace.domain.telemetry.TelemetryRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class TelemetryRepositoryImpl(
    private val api: TelemetryApi,
    private val dao: SellerMetricsDao,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val sessionIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val userIdProvider: () -> String? = { null },
    private val clock: () -> Instant = { Instant.now() }
) : TelemetryRepository {

    private val searchEvents = MutableStateFlow<List<SearchTelemetryEvent.FilterApplied>>(emptyList())
    private val listingCreatedEvents =
        MutableStateFlow<List<ListingTelemetryEvent.ListingCreated>>(emptyList())

    override fun observeSellerResponseMetrics(sellerId: String): Flow<SellerResponseMetrics?> =
        dao.observeMetrics(sellerId).map { entity -> entity?.toDomain() }

    override suspend fun getCachedSellerResponseMetrics(sellerId: String): SellerResponseMetrics? =
        dao.getMetrics(sellerId)?.toDomain()

    override suspend fun refreshSellerResponseMetrics(sellerId: String) {
        val dto = api.getSellerResponseMetrics(sellerId)
        val entity = dto.toEntity(now())
        dao.insertMetrics(entity)
    }

    override suspend fun isCacheExpired(sellerId: String, ttlMillis: Long): Boolean {
        val entity = dao.getMetrics(sellerId) ?: return true
        return now() - entity.fetchedAt > ttlMillis
    }

    override suspend fun recordSearchEvent(event: SearchTelemetryEvent.FilterApplied) {
        searchEvents.update { it + event }

        val payload = TelemetryEvent(
            event_type = "search.filter.applied",
            session_id = sessionIdProvider(),
            user_id = userIdProvider(),
            occurred_at = clock().toString(),
            properties = mapOf("filters" to event.filterKeys.sorted().joinToString(","))
        )

        runCatching { api.ingest(bearer = null, body = TelemetryBatch(listOf(payload))) }
    }

    override fun observeFilterFrequencies(): Flow<Map<String, Int>> =
        searchEvents.map { TelemetryAnalytics.filterFrequency(it) }

    override suspend fun getFilterFrequencies(): Map<String, Int> =
        TelemetryAnalytics.filterFrequency(searchEvents.value)

    override suspend fun recordListingCreated(event: ListingTelemetryEvent.ListingCreated) {
        listingCreatedEvents.update { it + event }

        val payload = TelemetryEvent(
            event_type = "listing.created",
            session_id = sessionIdProvider(),
            user_id = userIdProvider(),
            listing_id = event.listingId,
            occurred_at = event.createdAt.toString(),
            properties = mapOf(
                "category_id" to event.categoryId,
                "created_at" to event.createdAt.toString()
            )
        )

        runCatching { api.ingest(bearer = null, body = TelemetryBatch(listOf(payload))) }
    }

    override fun observeListingCreatedDailyCounts(): Flow<Map<LocalDate, Map<String, Int>>> =
        listingCreatedEvents.map { TelemetryAnalytics.listingCreatedDailyCounts(it) }

    override suspend fun getListingCreatedDailyCounts(): Map<LocalDate, Map<String, Int>> =
        TelemetryAnalytics.listingCreatedDailyCounts(listingCreatedEvents.value)

    companion object {
        fun create(context: android.content.Context): TelemetryRepositoryImpl {
            val db = TelemetryDatabaseProvider.get(context)
            return TelemetryRepositoryImpl(ApiClient.telemetryApi(), db.sellerMetricsDao())
        }
    }
}
